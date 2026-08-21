# Renderer Diagnostic Session Logging Plan

Status: implemented and live-validated; retention characterization remains an
active follow-up milestone.

This plan is a focused milestone ledger within the ongoing renderer-v2
refinement workstream. It does not create a separate renderer project. The
goal is to turn live visual-test sessions into stable diagnostic bundles that
another AI can inspect without reconstructing timing, settings, warnings, and
`Ctrl+F9` frame captures from unrelated console output and directories.

The parent status ledger remains
[renderer-v2-plan.md](renderer-v2-plan.md). Findings from live testing and
completed milestones should be reflected in both documents where relevant.

## Current Baseline

- Expanded `F6` shows a broad renderer telemetry snapshot, including recent
  timings, scene phases, resident chunk state, geometry, sprites, 2D command
  pressure, shadow work, and allocation summaries.
- Opt-in renderer telemetry prints lifetime and short-window summaries to
  standard output every `300` frames by default and on throttled slow-frame
  reports. Those reports are human-oriented strings and are not retained by
  the renderer itself.
- Opt-in `Ctrl+F9` capture writes a `12`-frame burst. Each rendered frame gets a
  separate timestamped directory containing layered PNGs plus analyzer-ready
  TSV/text inputs for world faces, scene commands, static ownership, sprites,
  depth, shader parity, and renderer 2D command limits.
- The offline capture analyzer validates and summarizes one frame directory at
  a time. It does not correlate the burst with the performance window before
  and after the capture.
- `spoiled-milk-client.log` currently records uncaught failures and selected
  scene-sync/movement-cache diagnostics. It does not contain the renderer
  telemetry stream or a capture index.
- There is no session manifest joining client revision, renderer settings,
  telemetry windows, slow frames, warnings, capture requests, capture results,
  and visual-test observations.

## Target Diagnostic Bundle

An explicitly enabled diagnostic launch should create one ignored,
timestamped session directory under `output/renderer-diagnostics/`:

```text
session-YYYYMMDD-HHMMSS/
  manifest.json
  telemetry.jsonl
  events.jsonl
  console.log
  captures/
    capture-index.jsonl
    capture-.../
  ai-summary.md
```

The existing per-frame PNG, TSV, and text capture contract should remain
analyzer-compatible. The session layer indexes those artifacts instead of
embedding large tables or images in the telemetry log.

## Stable Data Contract

- Use a versioned schema identifier in the manifest and every JSONL record.
- Use one complete JSON object per line so partially written sessions retain
  every complete earlier record.
- Keep field names stable, explicit, and grouped by owner, for example:
  `frame`, `clientLoop`, `scene`, `presentation`, `openGL`, `world`, `chunks`,
  `sprites`, `renderer2D`, `shadows`, `runtime`, and `allocations`.
- Store raw numeric values with units in field names (`Nanos`, `Bytes`,
  `Count`) instead of formatted values such as `1.25ms`.
- Distinguish sample scope explicitly: current/latest, recent rolling window,
  report window, lifetime, maximum, and configured limit must not be collapsed
  into ambiguous values.
- Give every record wall-clock time, monotonic session time, renderer frame
  sequence, and session identifier. Capture records additionally receive a
  burst identifier, burst-frame index, and relative artifact path.
- Treat additive fields as backward compatible. Schema-version changes are
  required for renamed fields or changed meaning.
- Do not record credentials, account identifiers, chat contents, private
  messages, authentication material, or raw packet payloads.

## Milestone 1: Session Ownership And Launch Contract

- [x] Add an opt-in diagnostic-session owner with one buffered writer per
      structured stream and deterministic close/flush behavior.
- [x] Add a development launcher option that enables renderer telemetry,
      `Ctrl+F9`, the structured session directory, and a visible `console.log`
      tee without changing normal client launches.
- [x] Write `manifest.json` with schema version, session identifier, start/end
      state, client commit/branch supplied by the launcher, target mode,
      Java/OS details, and a privacy-filtered active renderer setting inventory.
- [x] Keep output under ignored `output/renderer-diagnostics/` and make the
      chosen absolute session path obvious at launch.
- [x] Flush at report and capture boundaries. Avoid per-frame filesystem I/O.
- [x] Bound structured and console logs with a configurable byte budget. Keep
      explicit capture artifacts intact rather than automatically deleting
      ignored diagnostic sessions.

Acceptance:

- A diagnostic launch creates exactly one identifiable session bundle.
- A normal launch creates no new diagnostic files and pays no ongoing logging
  cost beyond the existing disabled checks.
- Abrupt termination may omit final metadata but leaves prior JSONL records
  parseable.

Implementation checkpoint:

- `./scripts/run-client.sh --live --renderer-diagnostics` creates a session
  under `output/renderer-diagnostics/`, enables renderer telemetry and
  `Ctrl+F9`, redirects the existing runtime log, and mirrors visible client
  output through a bounded console log.
- The Java session owner writes versioned manifests/events, filters sensitive
  setting names, correlates uncaught exceptions, and remains inert unless the
  diagnostic flag is enabled.

## Milestone 2: Comprehensive Structured Telemetry

- [x] Refactor renderer reporting so the console formatter and structured
      writer consume the same raw snapshot rather than maintaining competing
      metric lists.
- [x] Include every metric currently exposed through expanded `F6` or periodic
      renderer telemetry, including frame/client-loop timings, scene phases,
      OpenGL phases, dropped frames, world/chunk geometry and churn, texture
      work, sprite ownership/replay, 2D command limits and drops, shadows,
      renderer reasons/states, allocation estimates, and source/target render
      dimensions.
- [x] Add relevant runtime context: heap used/committed/max, process uptime,
      available processors, and per-collector GC count/time deltas.
- [x] Emit on the existing report cadence, on slow-frame reports, immediately
      before a `Ctrl+F9` burst, and immediately after the burst completes.
- [x] Preserve the current console report and expanded `F6` behavior while the
      structured output is validated.
- [x] Add contract tests proving representative F6/console fields exist in the
      JSONL schema with raw values and documented units.

Acceptance:

- An AI can determine whether a visual incident coincided with CPU/render
  timing, frame drops, GC, allocation, chunk churn, command overflow, texture
  pressure, or capture overhead without scraping console prose.
- Structured logging does not add a per-frame helper allocation or materially
  alter non-capture frame timing.

Implementation checkpoint:

- Report and slow-frame boundaries now write raw numeric telemetry from the
  same `StageStats` and `CounterStats` objects used by existing output. Stable
  fields expose lifetime, report-window, recent, latest, maximum, and total
  values without parsing formatted milliseconds or slash-delimited summaries.
- The record also includes frame context, configured command limits, renderer
  state reasons, allocation estimates, JVM heap/runtime state, and GC deltas.
  Diagnostic-disabled clients never construct these report records.

## Milestone 3: Events And `Ctrl+F9` Correlation

- [x] Write structured events for session start/stop, slow frames, 2D overflow,
      renderer warnings, region/resident-chunk transitions, capture request,
      capture frame start/completion/failure, and uncaught client failures.
- [x] Index all frames in a capture burst with their telemetry sequence,
      directory, layer list, failure state, and analyzer input inventory.
- [x] Keep existing per-frame capture paths valid for the current analyzer;
      use relative paths from the session bundle for portability.
- [x] Record capture readback/write timing separately so diagnostic overhead is
      visible and remains excluded from normal renderer phase totals.
- [x] Make repeated warning/event records machine-deduplicable with stable
      event types and reason fields rather than message parsing.

Acceptance:

- A reported visual problem can be tied to exact captured frames and the
  telemetry immediately surrounding them.
- A failed or partial burst is explicit in the index and does not masquerade
  as a complete capture.

Implementation checkpoint:

- Burst requests now emit pre/post telemetry records and typed lifecycle
  events. Each frame contributes started/final index records with stable
  burst/frame identity, relative paths, artifact inventory, failure state,
  renderer sequence, total frame span, and separately attributed input,
  layer, finish, and aggregate capture-work timings.
- Typed session events also retain slow frames, 2D overflow, world-section
  loads, chunk/shadow/resident reason transitions, OpenGL messages, and client
  exceptions. Existing standalone frame directories remain valid inputs for
  the original capture analyzer.
- The first live session exposed excessive reason-transition noise because
  normal animated-object work alternates `steady` and
  `animated-object-signature` within ordinary rendering. Reason events are now
  limited to one record per type per second, with suppressed transition counts
  aggregated and flushed on shutdown. Interrupting the bounded console mirror
  also exits cleanly without adding a Python traceback to the diagnostic log.

## Milestone 4: AI-Oriented Session Analyzer

- [x] Add an offline analyzer that validates the manifest and JSONL streams,
      invokes or reuses the existing per-frame capture analysis, and tolerates
      older standalone frame captures.
- [x] Generate `ai-summary.md` containing session identity, duration, renderer
      modes, worst timing windows, recent/lifetime drift, frame drops, GC/heap
      changes, chunk churn, command pressure, warnings, capture failures, and
      links to noteworthy capture frames.
- [x] Rank likely correlations without presenting correlation as proven cause.
      Examples include a frame-time spike beside GC activity, section loading,
      chunk uploads, atlas pressure, or command overflow.
- [x] Support a concise default report and a verbose mode that exposes raw
      record paths and validation details.
- [x] Add fixtures for clean sessions, partial last lines, missing optional
      fields, schema incompatibility, failed capture frames, overflow, GC
      pressure, and older capture-only directories.

Acceptance:

- The manager can provide one session directory plus visual observations and
  receive a reproducible, evidence-linked refinement recommendation.
- The summary remains compact enough to inspect directly while preserving
  paths to raw evidence.

Usage:

```bash
./scripts/run-client.sh --live --renderer-diagnostics
python3 scripts/analyze-renderer-session.py \
  output/renderer-diagnostics/session-... --strict --analyze-captures
```

The analyzer always prints its Markdown report and writes `ai-summary.md`
unless `--no-write` is supplied. `--verbose` includes the full event inventory;
`--analyze-captures` runs the established strict frame analyzer for completed
indexed frames.

### First live session finding

- A roughly `16`-minute live route through several dense NPC, sprite, and
  scenery areas was visually accepted: world visuals, shadows, and animations
  all looked correct.
- The corrected analyzer now ranks actual OpenGL render windows instead of the
  lightweight framebuffer handoff. Client-loop p50/p95/p99 was
  `16.666/17.683/18.591ms`; OpenGL render p50/p95/p99 was
  `6.673/9.139/9.782ms`; the worst sampled render window was `10.057ms`.
  There were no renderer slow-frame events or client exceptions.
- OpenGL reported `57,277` presented and `696` replaced pending frames
  (`1.20%`). This did not produce visible instability and the latest windows
  were generally near the expected cadence, so treat it as a trend to compare
  rather than an immediate visual defect.
- GC accounted for about `0.78%` of sampled session time at `2.93ms` per
  collection on average. The late sampled heap floor was about `270MB` above
  the early floor after extensive area traversal. That is a retention signal,
  not proof of a leak; a longer idle/relog comparison should precede JVM or
  cache changes.
- Primitive command capture reached `4096` and accumulated `41,926` drops only
  in the first report window. All `32` overflow events aligned exactly with the
  session's `32` world-section loads, while current dense-area frames reported
  zero drops. Do not grow the cap based on this alone; first determine whether
  load-transition primitives are intentionally disposable or visually
  relevant.
- No `Ctrl+F9` burst was present in the initially reviewed session, so live
  artifact-index and strict per-frame validation were initially pending.
- The subsequent `Ctrl+F9` burst completed all `12` frames with zero capture
  failures and complete indexed artifacts. Every frame passed the established
  strict capture analyzer. Across the burst, captured world faces stayed at
  `954..956`, world-sprite commands stayed at `709..725`, and there were zero
  missing anchors, zero suspicious visibility cases, zero occlusion
  disagreements, and zero renderer 2D command drops.
- Full capture is intentionally diagnostic-heavy: the burst took `25.500s`,
  averaging `2.108s` of synchronous capture work per frame and replacing
  `1,517` pending frames while only `12` capture frames were presented. The
  session analyzer now isolates indexed burst intervals from normal timing,
  dropped-frame, GC, heap, and worst-window summaries. If future temporal bugs
  require close consecutive frames, add a separate compact capture profile
  rather than weakening the current full evidence bundle silently.

## Ongoing Visual Refinement Loop

For each manager-reported visual test:

1. Record the observation, location/action context, expected appearance, and
   approximate capture time or burst identifier.
2. Validate the session and relevant capture frames before proposing renderer
   changes.
3. Separate direct evidence, likely correlation, and speculation.
4. Implement the narrowest refinement that addresses the supported cause.
5. Add or update a guardrail, repeat the relevant capture route, and compare
   diagnostics before and after.
6. Update this plan and `renderer-v2-plan.md` with the finding, result, known
   risk, and completed milestone.
7. Create a pushed checkpoint after each meaningful completed piece and keep
   `feat/renderer-v2-refinement` ACTIVE. Do not create a final handoff unless
   the manager explicitly requests a final roundup.

## Validation

- [x] Client compiles with Java 8.
- [x] Existing renderer guardrail and capture-analyzer suites remain green.
- [x] New schema/bounds/privacy/analyzer fixtures pass.
- [x] Diagnostic-disabled runtime produces no session bundle.
- [x] Diagnostic-enabled fixture produces a parseable bundle and bounded
      console output.
- [x] A real `Ctrl+F9` burst is indexed and analyzable from the session root.
- [x] Logging overhead is measured with capture inactive and does not
      materially regress recent frame timing or allocation behavior.

## Active Retention Follow-Up

The first accepted session's sampled heap floor rose during a long route and
rose sharply around the intentionally intrusive full capture burst. This is
not leak proof. The approved
[renderer-retention-characterization-plan.md](../completed-work-plans/renderer-retention-characterization-plan.md)
adds post-collection old-generation, per-collector, direct-buffer, and
account-free login-epoch evidence, then uses a no-capture idle/route/relogin
test to decide whether focused retention profiling is warranted.

Completed result: the `444.6s` no-capture route showed client-loop p95
`17.215ms`, OpenGL render p95 `8.688ms`, GC cost `0.61%`, no slow-frame or
exception events, and an old-generation plateau that remained exact across
logout/relogin and the second route. Direct buffers fluctuated below `77MB`
with evidence of reclamation. No heap/cache change or focused profiler pass is
warranted; retain this telemetry for regression monitoring.
