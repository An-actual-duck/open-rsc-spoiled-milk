package orsc;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

final class RendererDiagnosticSession {
	static final int SCHEMA_VERSION = 1;
	static final String SCHEMA_NAME = "renderer-diagnostics";

	private static final Object LOCK = new Object();
	private static final String ENABLED_PROPERTY = "spoiledmilk.rendererDiagnostics";
	private static final String ENABLED_ENV = "SPOILED_MILK_RENDERER_DIAGNOSTICS";
	private static final String SESSION_DIR_PROPERTY = "spoiledmilk.rendererDiagnosticSessionDir";
	private static final String SESSION_DIR_ENV = "SPOILED_MILK_RENDERER_DIAGNOSTIC_SESSION_DIR";
	private static final String MAX_LOG_BYTES_PROPERTY = "spoiledmilk.rendererDiagnosticMaxLogBytes";
	private static final String MAX_LOG_BYTES_ENV = "SPOILED_MILK_RENDERER_DIAGNOSTIC_MAX_LOG_BYTES";
	private static final long DEFAULT_MAX_LOG_BYTES = 256L * 1024L * 1024L;
	private static final long REASON_EVENT_INTERVAL_NANOS = 1_000_000_000L;
	private static final boolean ENABLED = readBoolean(ENABLED_PROPERTY, ENABLED_ENV);
	private static final long MAX_LOG_BYTES = Math.max(
		1024L * 1024L,
		readLong(MAX_LOG_BYTES_PROPERTY, MAX_LOG_BYTES_ENV, DEFAULT_MAX_LOG_BYTES));
	private static final long EVENT_RESERVED_BYTES = Math.min(
		8L * 1024L * 1024L,
		Math.max(256L * 1024L, MAX_LOG_BYTES / 8L));

	private static boolean started;
	private static boolean closed;
	private static boolean truncated;
	private static boolean bulkTruncated;
	private static boolean eventTruncated;
	private static long startedNanos;
	private static long structuredBytes;
	private static String startedAt;
	private static String endedAt;
	private static String sessionId;
	private static String openGLRenderer = "unknown";
	private static String openGLVersion = "unknown";
	private static String openGLVendor = "unknown";
	private static File sessionDirectory;
	private static PrintWriter telemetryWriter;
	private static PrintWriter eventWriter;
	private static PrintWriter captureIndexWriter;
	private static final Map<String, ReasonEventState> reasonEventStates =
		new java.util.LinkedHashMap<String, ReasonEventState>();

	private RendererDiagnosticSession() {
	}

	static boolean isEnabled() {
		return ENABLED;
	}

	static void start() {
		if (!ENABLED) {
			return;
		}
		synchronized (LOCK) {
			if (started) {
				return;
			}
			try {
				sessionDirectory = resolveSessionDirectory();
				if (!sessionDirectory.mkdirs() && !sessionDirectory.isDirectory()) {
					throw new IOException("could not create " + sessionDirectory.getAbsolutePath());
				}
				sessionId = sessionDirectory.getName();
				startedAt = timestamp();
				startedNanos = System.nanoTime();
				telemetryWriter = openWriter(new File(sessionDirectory, "telemetry.jsonl"));
				eventWriter = openWriter(new File(sessionDirectory, "events.jsonl"));
				File captureDirectory = new File(sessionDirectory, "captures");
				if (!captureDirectory.mkdirs() && !captureDirectory.isDirectory()) {
					throw new IOException("could not create " + captureDirectory.getAbsolutePath());
				}
				captureIndexWriter = openWriter(new File(captureDirectory, "capture-index.jsonl"));
				started = true;
				BoundaryLoadingDiagnostics.initialize();
				writeManifest("open");
				Record event = newRecord("event");
				event.string("eventType", "session.start");
				event.string("sessionDirectory", sessionDirectory.getAbsolutePath());
				writeEvent(event);
				Runtime.getRuntime().addShutdownHook(
					new Thread(RendererDiagnosticSession::close, "renderer-diagnostic-session-close"));
				System.out.println(
					"[renderer diagnostics] session=" + sessionDirectory.getAbsolutePath());
			} catch (IOException e) {
				closeWriter(telemetryWriter);
				closeWriter(eventWriter);
				closeWriter(captureIndexWriter);
				telemetryWriter = null;
				eventWriter = null;
				captureIndexWriter = null;
				System.err.println("[renderer diagnostics] could not start: " + e.getMessage());
			}
		}
	}

	static void close() {
		synchronized (LOCK) {
			if (!started || closed) {
				return;
			}
			BoundaryLoadingDiagnostics.flushOnSessionClose();
			flushSuppressedReasonEvents();
			Record event = newRecord("event");
			event.string("eventType", "session.stop");
			writeEvent(event);
			endedAt = timestamp();
			closed = true;
			try {
				writeManifest(truncated ? "truncated" : "closed");
			} catch (IOException e) {
				System.err.println("[renderer diagnostics] could not finalize manifest: " + e.getMessage());
			}
			closeWriter(telemetryWriter);
			closeWriter(eventWriter);
			closeWriter(captureIndexWriter);
			telemetryWriter = null;
			eventWriter = null;
			captureIndexWriter = null;
		}
	}

	static Record newTelemetryRecord(String trigger, long rendererFrameSequence) {
		if (!ensureStarted()) {
			return null;
		}
		synchronized (LOCK) {
			Record record = newRecord("telemetry");
			record.string("trigger", trigger);
			record.number("rendererFrameSequence", rendererFrameSequence);
			return record;
		}
	}

	static void writeTelemetry(Record record) {
		if (record == null) {
			return;
		}
		synchronized (LOCK) {
			writeRecord(telemetryWriter, record, false);
		}
	}

	static void recordEvent(String eventType, String detail) {
		if (!ensureStarted()) {
			return;
		}
		synchronized (LOCK) {
			Record event = newRecord("event");
			event.string("eventType", eventType);
			if (detail != null && !detail.isEmpty()) {
				event.string("detail", detail);
			}
			writeEvent(event);
		}
	}

	static void recordOpenGLDevice(String renderer, String version, String vendor) {
		if (!ensureStarted()) {
			return;
		}
		synchronized (LOCK) {
			openGLRenderer = safeDiagnosticValue(renderer);
			openGLVersion = safeDiagnosticValue(version);
			openGLVendor = safeDiagnosticValue(vendor);
			Record event = newRecord("event");
			event.string("eventType", "renderer.opengl.device");
			event.string("renderer", openGLRenderer);
			event.string("version", openGLVersion);
			event.string("vendor", openGLVendor);
			writeEvent(event);
			try {
				writeManifest("open");
			} catch (IOException e) {
				System.err.println(
					"[renderer diagnostics] could not update OpenGL manifest fields: "
						+ e.getMessage());
			}
		}
	}

	static Record newEventRecord(String eventType) {
		if (!ensureStarted()) {
			return null;
		}
		synchronized (LOCK) {
			Record event = newRecord("event");
			event.string("eventType", eventType);
			return event;
		}
	}

	static void writeEventRecord(Record event) {
		if (event == null) {
			return;
		}
		synchronized (LOCK) {
			writeEvent(event);
		}
	}

	static void recordCaptureFrame(
		String status,
		long burstId,
		int burstFrameIndex,
		int captureSequence,
		long rendererFrameSequence,
		File captureDirectory,
		long captureSpanNanos,
		long inputCaptureNanos,
		long layerCaptureNanos,
		long finishCaptureNanos,
		boolean failed,
		String failure,
		String[] artifacts) {
		if (!ensureStarted()) {
			return;
		}
		synchronized (LOCK) {
			Record record = newRecord("capture");
			record.string("status", status);
			record.number("burstId", burstId);
			record.number("burstFrameIndex", burstFrameIndex);
			record.number("captureSequence", captureSequence);
			record.number("rendererFrameSequence", rendererFrameSequence);
			if (captureDirectory != null) {
				record.string("path", relativeSessionPath(captureDirectory));
			}
			record.number("captureSpanNanos", captureSpanNanos);
			record.number("inputCaptureNanos", inputCaptureNanos);
			record.number("layerCaptureNanos", layerCaptureNanos);
			record.number("finishCaptureNanos", finishCaptureNanos);
			record.number(
				"captureWorkNanos",
				Math.max(0L, inputCaptureNanos)
					+ Math.max(0L, layerCaptureNanos)
					+ Math.max(0L, finishCaptureNanos));
			record.bool("failed", failed);
			if (failure != null && !failure.isEmpty()) {
				record.string("failure", failure);
			}
			record.strings("artifacts", artifacts);
			writeRecord(captureIndexWriter, record, false);

			Record event = newRecord("event");
			event.string("eventType", "capture.frame." + status);
			event.number("burstId", burstId);
			event.number("burstFrameIndex", burstFrameIndex);
			event.number("captureSequence", captureSequence);
			event.number("rendererFrameSequence", rendererFrameSequence);
			if (captureDirectory != null) {
				event.string("path", relativeSessionPath(captureDirectory));
			}
			event.bool("failed", failed);
			if (failure != null && !failure.isEmpty()) {
				event.string("failure", failure);
			}
			writeEvent(event);
		}
	}

	static void recordThrowable(String context, Throwable throwable) {
		if (!ensureStarted()) {
			return;
		}
		synchronized (LOCK) {
			Record event = newRecord("event");
			event.string("eventType", "client.exception");
			event.string("context", context);
			if (throwable != null) {
				event.string("exceptionClass", throwable.getClass().getName());
				event.string("exceptionMessage", throwable.getMessage());
			}
			writeEvent(event);
		}
	}

	static void recordReasonChange(
		String eventType,
		String previousReason,
		String currentReason,
		long rendererFrameSequence) {
		if (!ensureStarted()) {
			return;
		}
		synchronized (LOCK) {
			long now = System.nanoTime();
			ReasonEventState state = reasonEventStates.get(eventType);
			if (state == null) {
				state = new ReasonEventState();
				reasonEventStates.put(eventType, state);
			}
			state.previousReason = previousReason;
			state.currentReason = currentReason;
			state.rendererFrameSequence = rendererFrameSequence;
			if (state.lastWriteNanos != 0L
				&& now - state.lastWriteNanos < REASON_EVENT_INTERVAL_NANOS) {
				state.suppressedTransitions++;
				return;
			}
			writeReasonEvent(eventType, state, false);
			state.lastWriteNanos = now;
			state.suppressedTransitions = 0;
		}
	}

	static File getSessionDirectory() {
		if (!ensureStarted()) {
			return null;
		}
		synchronized (LOCK) {
			return sessionDirectory;
		}
	}

	private static boolean ensureStarted() {
		if (!ENABLED) {
			return false;
		}
		if (!started) {
			start();
		}
		return started && !closed;
	}

	private static Record newRecord(String recordType) {
		Record record = new Record();
		record.string("schema", SCHEMA_NAME);
		record.number("schemaVersion", SCHEMA_VERSION);
		record.string("recordType", recordType);
		record.string("sessionId", sessionId);
		record.string("timestamp", timestamp());
		record.number("sessionElapsedNanos", Math.max(0L, System.nanoTime() - startedNanos));
		return record;
	}

	private static void writeEvent(Record event) {
		writeRecord(eventWriter, event, true);
	}

	private static void flushSuppressedReasonEvents() {
		for (Map.Entry<String, ReasonEventState> entry : reasonEventStates.entrySet()) {
			ReasonEventState state = entry.getValue();
			if (state.suppressedTransitions <= 0) {
				continue;
			}
			writeReasonEvent(entry.getKey(), state, true);
			state.suppressedTransitions = 0;
		}
	}

	private static void writeReasonEvent(
		String eventType,
		ReasonEventState state,
		boolean finalFlush) {
		Record event = newRecord("event");
		event.string("eventType", eventType);
		event.string("previousReason", state.previousReason);
		event.string("currentReason", state.currentReason);
		event.number("rendererFrameSequence", state.rendererFrameSequence);
		event.number("suppressedTransitions", state.suppressedTransitions);
		event.bool("finalFlush", finalFlush);
		writeEvent(event);
	}

	private static void writeRecord(
		PrintWriter writer,
		Record record,
		boolean eventRecord) {
		if (writer == null || record == null
			|| (eventRecord ? eventTruncated : bulkTruncated)) {
			return;
		}
		String line = record.toJson();
		long estimatedBytes = line.length() * 2L + 2L;
		long limit = eventRecord
			? MAX_LOG_BYTES
			: MAX_LOG_BYTES - EVENT_RESERVED_BYTES;
		if (structuredBytes + estimatedBytes > limit) {
			truncated = true;
			if (eventRecord) {
				eventTruncated = true;
			} else {
				bulkTruncated = true;
			}
			try {
				writeManifest("open");
			} catch (IOException e) {
				System.err.println(
					"[renderer diagnostics] could not record log truncation in manifest: "
						+ e.getMessage());
			}
			System.err.println(
				"[renderer diagnostics] "
					+ (eventRecord ? "event" : "bulk telemetry")
					+ " log budget reached; further "
					+ (eventRecord ? "event" : "bulk")
					+ " records are disabled.");
			return;
		}
		long writeStartedNanos =
			BoundaryLoadingDiagnostics.now();
		writer.println(line);
		writer.flush();
		BoundaryLoadingDiagnostics.recordDiskRead(
			eventRecord
				? "diagnostic-event-log"
				: "diagnostic-bulk-log",
			estimatedBytes,
			writeStartedNanos,
			writeStartedNanos == 0L
				? 0L : System.nanoTime() - writeStartedNanos);
		structuredBytes += estimatedBytes;
	}

	private static void writeManifest(String state) throws IOException {
		File manifest = new File(sessionDirectory, "manifest.json");
		File pending = new File(sessionDirectory, "manifest.json.pending");
		Record record = new Record();
		record.string("schema", SCHEMA_NAME);
		record.number("schemaVersion", SCHEMA_VERSION);
		record.string("sessionId", sessionId);
		record.string("state", state);
		record.string("startedAt", startedAt);
		if (endedAt != null) {
			record.string("endedAt", endedAt);
		}
		record.number("maxStructuredLogBytes", MAX_LOG_BYTES);
		record.number("reservedEventLogBytes", EVENT_RESERVED_BYTES);
		record.bool("bulkLogTruncated", bulkTruncated);
		record.bool("eventLogTruncated", eventTruncated);
		record.string("branch", readEnvironment("SPOILED_MILK_CLIENT_BRANCH", "unknown"));
		record.string("commit", readEnvironment("SPOILED_MILK_CLIENT_COMMIT", "unknown"));
		record.string("targetMode", readEnvironment("SPOILED_MILK_CLIENT_TARGET_MODE", "unknown"));
		record.string("runtime.javaVersion", System.getProperty("java.version", "unknown"));
		record.string("runtime.javaVendor", System.getProperty("java.vendor", "unknown"));
		record.string("runtime.vmName", System.getProperty("java.vm.name", "unknown"));
		record.string("runtime.osName", System.getProperty("os.name", "unknown"));
		record.string("runtime.osVersion", System.getProperty("os.version", "unknown"));
		record.string("runtime.osArch", System.getProperty("os.arch", "unknown"));
		record.number("runtime.availableProcessors", Runtime.getRuntime().availableProcessors());
		record.number("runtime.processStartMillis", ManagementFactory.getRuntimeMXBean().getStartTime());
		record.string("openGL.renderer", openGLRenderer);
		record.string("openGL.version", openGLVersion);
		record.string("openGL.vendor", openGLVendor);
		record.string("files.telemetry", "telemetry.jsonl");
		record.string("files.events", "events.jsonl");
		record.string("files.console", "console.log");
		record.string("files.captureRoot", "captures");
		record.string("files.captureIndex", "captures/capture-index.jsonl");
		String jfrPath = readEnvironment("SPOILED_MILK_CLIENT_JFR_PATH", "");
		if (!jfrPath.isEmpty()) {
			record.string("files.jfr", relativeSessionPath(new File(jfrPath)));
		}
		appendRendererSettings(record);
		try (PrintWriter writer = openWriter(pending)) {
			writer.println(record.toJson());
		}
		if (manifest.exists() && !manifest.delete()) {
			throw new IOException("could not replace " + manifest.getAbsolutePath());
		}
		if (!pending.renameTo(manifest)) {
			throw new IOException("could not publish " + manifest.getAbsolutePath());
		}
	}

	private static void appendRendererSettings(Record record) {
		Properties properties = System.getProperties();
		List<String> propertyNames = new ArrayList<String>();
		for (String name : properties.stringPropertyNames()) {
			if (name.startsWith("spoiledmilk.") && !isSensitiveName(name)) {
				propertyNames.add(name);
			}
		}
		Collections.sort(propertyNames);
		for (String name : propertyNames) {
			record.string("settings.property." + name, properties.getProperty(name));
		}

		Map<String, String> environment = System.getenv();
		List<String> environmentNames = new ArrayList<String>();
		for (String name : environment.keySet()) {
			if (name.startsWith("SPOILED_MILK_") && !isSensitiveName(name)) {
				environmentNames.add(name);
			}
		}
		Collections.sort(environmentNames);
		for (String name : environmentNames) {
			record.string("settings.environment." + name, environment.get(name));
		}
	}

	private static boolean isSensitiveName(String name) {
		String normalized = name == null ? "" : name.toLowerCase();
		return normalized.contains("credential")
			|| normalized.contains("password")
			|| normalized.contains("secret")
			|| normalized.contains("token")
			|| normalized.contains("auth");
	}

	private static String safeDiagnosticValue(String value) {
		return value == null || value.trim().isEmpty() ? "unknown" : value.trim();
	}

	private static File resolveSessionDirectory() {
		String configured = System.getProperty(SESSION_DIR_PROPERTY);
		if (configured == null || configured.trim().isEmpty()) {
			configured = System.getenv(SESSION_DIR_ENV);
		}
		if (configured != null && !configured.trim().isEmpty()) {
			return absoluteFile(configured.trim());
		}
		File root = absoluteFile("output/renderer-diagnostics");
		String generatedId = "session-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withZone(java.time.ZoneId.systemDefault())
			.format(Instant.now());
		return new File(root, generatedId);
	}

	private static File absoluteFile(String path) {
		File file = new File(path);
		if (!file.isAbsolute()) {
			file = new File(System.getProperty("user.dir", "."), path);
		}
		return file;
	}

	private static String relativeSessionPath(File file) {
		if (sessionDirectory == null || file == null) {
			return file == null ? "" : file.getPath();
		}
		String relative = sessionDirectory.toURI().relativize(file.toURI()).getPath();
		return relative == null || relative.isEmpty() ? file.getAbsolutePath() : relative;
	}

	private static PrintWriter openWriter(File file) throws IOException {
		return new PrintWriter(new BufferedWriter(new FileWriter(file, false)));
	}

	private static void closeWriter(PrintWriter writer) {
		if (writer != null) {
			writer.flush();
			writer.close();
		}
	}

	private static boolean readBoolean(String property, String environment) {
		String value = System.getProperty(property);
		if (value == null || value.trim().isEmpty()) {
			value = System.getenv(environment);
		}
		return value != null && Boolean.parseBoolean(value.trim());
	}

	private static long readLong(String property, String environment, long fallback) {
		String value = System.getProperty(property);
		if (value == null || value.trim().isEmpty()) {
			value = System.getenv(environment);
		}
		if (value == null || value.trim().isEmpty()) {
			return fallback;
		}
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static String readEnvironment(String name, String fallback) {
		String value = System.getenv(name);
		return value == null || value.trim().isEmpty() ? fallback : value.trim();
	}

	private static String timestamp() {
		return Instant.now().toString();
	}

	static final class Record {
		private final StringBuilder json = new StringBuilder(1024);
		private boolean hasFields;

		Record() {
			json.append('{');
		}

		Record string(String name, String value) {
			appendName(name);
			if (value == null) {
				json.append("null");
			} else {
				appendQuoted(value);
			}
			return this;
		}

		Record number(String name, long value) {
			appendName(name);
			json.append(value);
			return this;
		}

		Record number(String name, double value) {
			appendName(name);
			if (Double.isNaN(value) || Double.isInfinite(value)) {
				json.append("null");
			} else {
				json.append(value);
			}
			return this;
		}

		Record bool(String name, boolean value) {
			appendName(name);
			json.append(value);
			return this;
		}

		Record strings(String name, String[] values) {
			appendName(name);
			json.append('[');
			if (values != null) {
				for (int i = 0; i < values.length; i++) {
					if (i > 0) {
						json.append(',');
					}
					appendQuoted(values[i] == null ? "" : values[i]);
				}
			}
			json.append(']');
			return this;
		}

		Record numbers(String name, long[] values) {
			appendName(name);
			json.append('[');
			if (values != null) {
				for (int i = 0; i < values.length; i++) {
					if (i > 0) {
						json.append(',');
					}
					json.append(values[i]);
				}
			}
			json.append(']');
			return this;
		}

		String toJson() {
			return json.toString() + '}';
		}

		private void appendName(String name) {
			if (hasFields) {
				json.append(',');
			}
			hasFields = true;
			appendQuoted(name == null ? "" : name);
			json.append(':');
		}

		private void appendQuoted(String value) {
			json.append('"');
			for (int i = 0; i < value.length(); i++) {
				char ch = value.charAt(i);
				switch (ch) {
					case '"':
						json.append("\\\"");
						break;
					case '\\':
						json.append("\\\\");
						break;
					case '\b':
						json.append("\\b");
						break;
					case '\f':
						json.append("\\f");
						break;
					case '\n':
						json.append("\\n");
						break;
					case '\r':
						json.append("\\r");
						break;
					case '\t':
						json.append("\\t");
						break;
					default:
						if (ch < 0x20) {
							json.append(String.format("\\u%04x", (int) ch));
						} else {
							json.append(ch);
						}
				}
			}
			json.append('"');
		}
	}

	private static final class ReasonEventState {
		private long lastWriteNanos;
		private int suppressedTransitions;
		private String previousReason = "unknown";
		private String currentReason = "unknown";
		private long rendererFrameSequence;
	}
}
