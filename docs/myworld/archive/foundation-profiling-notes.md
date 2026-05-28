# Foundation Profiling Notes

These notes are optional local diagnostics for optimization work. They are not
required for normal MyWorld development, and generated recordings should stay
out of git.

## Benchmark Artifacts

Use the existing benchmark scripts first:

```bash
./scripts/benchmark-matrix.sh
MYWORLD_BENCHMARK_SCENARIOS="players" ./scripts/benchmark-matrix.sh
```

Benchmark summaries and logs are written under:

```text
output/benchmarks/optimization/
output/logs/
```

## GC Logging

For Java 8 style GC logging, add JVM options locally when running the server:

```text
-verbose:gc
-XX:+PrintGCDetails
-XX:+PrintGCDateStamps
-Xloggc:output/benchmarks/optimization/gc.log
```

For Java 9+ unified logging:

```text
-Xlog:gc*,safepoint:file=output/benchmarks/optimization/gc.log:time,uptime,level,tags
```

Use GC logs to confirm whether a timing change came from allocation reduction
or simply from a different collection schedule.

## Java Flight Recorder

On Java versions with JFR support, prefer a short local recording around one
benchmark scenario:

```text
-XX:StartFlightRecording=filename=output/benchmarks/optimization/foundation.jfr,dumponexit=true,settings=profile
```

Keep recordings short and scenario-specific. Useful views include allocation
hot spots, lock profiles, socket activity, file I/O, and method sampling.

## Interpretation Rules

- Compare the same scenario against the same local database and config.
- Treat one run as a hint, not proof.
- Prefer player-load runs when evaluating packet, save, or view-area changes.
- Do not use forced GC timings as normal runtime evidence.
