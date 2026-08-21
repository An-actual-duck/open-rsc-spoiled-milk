# Renderer Retention Characterization Plan

Status: implemented, merged to `main`, and live-validated; continued monitoring
is part of ordinary renderer diagnostics rather than a separate active plan.

This is a short measurement milestone inside the ongoing renderer-v2
refinement workstream. It follows the accepted live visual/performance route
and the successful strict `Ctrl+F9` burst. It does not change renderer visuals,
command capacities, JVM heap limits, or ownership boundaries.

## Question To Answer

The accepted live session showed a rising sampled heap floor, with a larger
increase around the deliberately expensive full capture burst. Ordinary heap
occupancy alone cannot distinguish reusable committed space, short-lived
allocation pressure, retained old-generation objects, or native OpenGL/direct
buffer growth. The signal is therefore a reason to measure, not evidence of a
leak.

This milestone should determine whether a repeatable idle/route/logout/relogin
sequence leaves material post-collection or native-buffer growth after the
client returns to comparable state.

## Instrumentation

- [x] Preserve aggregate heap and GC fields for analyzer compatibility.
- [x] Record each JVM garbage collector's count/time and report-window deltas.
- [x] Record heap and non-heap memory pools, including usage, peak usage, and
      post-collection usage where the active JVM exposes it.
- [x] Record native buffer-pool count, used bytes, and total capacity, including
      the direct-buffer pool used by native/OpenGL paths.
- [x] Add account-free `client.login` and `client.logout` lifecycle events and
      exact diagnostic boundaries so one JVM session can be divided into
      comparable login epochs.
- [x] Extend `analyze-renderer-session.py` with old-generation, direct-buffer,
      and per-login-epoch retention summaries. Continue excluding full capture
      intervals from normal rankings.

## Controlled Live Route

Use a fresh diagnostic session and do not trigger `Ctrl+F9` during this test:

1. Log in and idle in the starting area for roughly two minutes.
2. Traverse the same representative route used for the accepted dense-area
   visual test, including several section boundaries.
3. Return to a comparable low-activity area and idle for roughly two minutes.
4. Log out and log back in without closing the client.
5. Repeat a shorter version of the route and finish with another idle period.
6. Close the client cleanly, run the session analyzer, and compare login epochs.

The route need not force a collection. If the JVM does not collect old
generation during the test, the correct result is insufficient post-GC
evidence rather than a forced `System.gc()` that changes normal client
behavior.

## Decision Rules

- Treat stable post-collection old-generation and direct-buffer floors across
  comparable epochs as closure of the current retention concern.
- Treat heap growth with stable post-collection/native floors as normal
  allocation/heap reuse unless hitch telemetry says otherwise.
- If post-collection old generation or direct buffers repeatedly grow after
  comparable logout/relogin cycles, identify the retaining owner with a
  focused profiler/heap-dump milestone before changing heap size or renderer
  caches.
- Keep full `Ctrl+F9` capture out of this route. Capture allocation is already
  measured as a separate intrusive diagnostic workload.

## Validation And Completion

- [x] Java 8 client build passes.
- [x] Diagnostic-disabled launch remains inert.
- [x] Diagnostic session/analyzer fixtures cover the new stable fields and
      lifecycle epoch summary.
- [x] Full renderer guardrail suite passes.
- [x] A controlled two-epoch live session is captured and analyzed.
- [x] Findings and the resulting next renderer milestone are recorded in this
      plan, `renderer-diagnostic-session-logging-plan.md`, and
      `renderer-v2-plan.md`.

## Live Finding

The controlled no-capture session ran for `444.6s` across two login epochs,
five in-game section loads, a dense-area idle/traversal, a teleport to a quiet
area, logout/relogin, a shorter second traversal, and a final idle.

- `PS Old Gen` post-collection occupancy changed only twice, both during the
  first epoch: roughly `48.5MB -> 321.0MB -> 398.8MB`. It then remained exactly
  `398,790,048` bytes through the first quiet idle, logout, relog, the complete
  second route, and final idle. The two `PS MarkSweep` collections consumed
  `182ms` total. This is a one-time warm-up/route plateau, not evidence of
  continuing per-login retention.
- Direct-buffer use fluctuated and was repeatedly reclaimed rather than rising
  monotonically. It stayed below `76.1MB`; late first-epoch samples ranged
  roughly `54.6..72.4MB`, while late second-epoch samples ranged
  `62.3..76.1MB`. The modest higher floor after a different region was loaded,
  alongside lower buffer counts, is consistent with different resident-region
  products. It does not justify native-buffer profiling now.
- Aggregate GC cost remained small: `0.61%` of sampled duration, with no
  renderer slow-frame or exception events. Client-loop p95 was `17.215ms`,
  effectively matching the prior accepted diagnostic route, while OpenGL
  render p95 was `8.688ms`.

Decision: close the current retention concern without changing heap size,
forcing GC, or taking a heap dump. Keep the fields in normal diagnostic
sessions and reopen focused profiling only if post-collection old-generation
or direct-buffer floors continue rising across comparable later epochs.

The recommended next renderer milestone is a parity-preserving material-family
foundation: introduce explicit stable classifications and telemetry before
changing visible shader response.

Checkpoint the implemented instrumentation and tests before the live route,
then keep the branch ACTIVE. This is not a final handoff.
