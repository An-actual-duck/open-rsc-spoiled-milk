# Browser geometry preparation candidate

Baseline: `325bf42b60fe411705f9f14c0a3ef6debabe24a7`.
Status: CPU-verified experiment, ordinary default **OFF**. Actual browser timing
and any adoption decision belong to the manager. No FPS gain is claimed here.

## Source findings and change

The browser already writes its final 28-float vertex array directly. The JS
boundary borrows an existing Float32Array when available, and bufferData or
bufferSubData consumes it synchronously. There is no remaining full-size native
vertex-buffer copy to remove in that path. Retaining caller arrays or caching
unversioned mutable geometry would introduce different lifetime risks.

Material resolution is repeated for each of a triangle's three expanded
vertices. The candidate resolves the effective texture ID, raw material color,
material color and source texture once per triangle during one packing call.
Every light, coordinate, normal, UV, family, variation and terrain-blend field
still goes through the existing formulas. Brightness remains evaluated at its
existing per-vertex point. There is no cross-frame material or pixel cache.

The shared writer takes optional prepared inputs. Native, browser OFF and
browser ON each make **one writer invocation per vertex**, so the comparison
does not add a baseline-only wrapper call. Native and browser OFF retain their
original resolution order, direct-buffer ownership and failure behavior.
Their writer source is refactored; their packed behavior is verified unchanged.

Only a mesh with exactly three expanded vertices per declared triangle uses
reuse. Other shapes take the baseline method, preserving partial/malformed
mesh behavior. Source topology, material grouping, index capacity, GPU transfer
layout, cache identity, upload budgets, shadow casters and readiness are unchanged.
Compact shadow-only buffers and the parked fog-demand selection are outside
this change.

## Private control and ownership

Java selection is `spoiledmilk.browserGeometryTransferReuse=true|false`, default
false. Actual selected policy is available from the one-time public getter
`BrowserWebGLBridge.isGeometryTransferReuseEnabled()`.

The separate JS helper exports
`browserGeometryTransferPolicy(mode, URLSearchParams)`. Only benchmark WebGL
URLs map `geometrytransfer=on|off` to that Java property, default OFF. Ordinary
and software queries return no override. The helper returns `properties` and
`requested` for the existing wrapper convention.

The repeatable-profiler worker owns the `browser.js` and `BrowserBootstrap`
import/property/one-time receipt union. This branch deliberately does not edit
those files. Before actual browser A/B measurement, that integration must be
present and its requested/actual receipt must agree. No per-frame native policy
callback is required, and changing this policy requires a fresh Java session.

## Independent CPU evidence

`test-browser-geometry-transfer.py` compiles the exact frozen baseline renderer
and current OFF/ON renderer into three separate JVM classpaths. It executes the
maintained production mesh/RSModel corpus and actual native plain/atlas/proof
writers. It does not reconstruct expected packing arithmetic.

Both Java 8 and Java 21 pass:

- 600 cases, 3,514,140 raw float fields and 14,063,764 bytes per policy,
  byte-identical to the frozen source. Browser and native streams are included.
- 1,200 additional edge cases: 1,107 successful streams and 93 matching baseline
  failure outcomes; 7,068,367 bytes per policy on Java 8 and 7,071,502 on Java 21
  (whose exception messages are more descriptive), each identical within its
  runtime. Includes vertex, grouped index/material and exception data, incomplete metadata, odd vertex counts, invalid or
  truncated indices, signed levels, rebases, 64/128 textures, texture metadata
  changes, missing/transparent/fallback materials and NaN/infinite UVs.
- Test-only instrumentation counts actual helper invocations without replacing
  formulas. Effective/raw/material resolution counts fall respectively from
  117,855/41,835/76,020 to 39,285/13,945/25,340: exactly threefold on that fully
  expanded corpus. This is a call-count result, not a timing prediction.

The edge runner disables HotSpot's repeated-fast-throw detail elision so the
baseline exception messages remain observable in every process. It does not
relax failures or modify production exception handling.

`test-browser-geometry-transfer-bridge.py` runs the real bridge's existing
recording-backend corpus under all eight LRU/active-cache, animated immutable/
reuse and preparation OFF/ON combinations on Java 8 and Java 21. It checks
actual method entry, latest pose output, topology/material changes, rebases,
negative levels, cache/budget accounting, failed upload/replacement and eviction.

Generated evidence is under `output/tests/browser-geometry-transfer/`, with
separate runtime directories. The main stream SHA256 is
`10e881bea9f2d64b4ca4e60dfa2bb42f0fbb386cdd82183a5e88b0138be1ece5`;
the edge stream SHA256 on Java 8 is
`9bcfa99c8957947c0f516a584e315d63416d26f43b0cff0197aa0bbb7ecdb299`.

Additional checks: fresh Java-8-target client build, private-policy unit test,
existing array packing/material tests and full renderer CPU guardrails.
The layout checker still reports inherited uncategorized ai3 output-root
entries; new writers use the declared tests category. Historical outputs were
preserved.

## Acceptance still required

Use the same final JAR with explicit OFF/ON startup policies, identical camera,
region and settings, both-mode warmup and actual requested/selected receipts.
Record packing/transfer work, geometry counts and fresh source-world FPS;
distinguish steady animated repacking from initial/region loading. Source data
bytes are identical, but desktop JVM call-count proofs do not establish a
CheerpJ improvement. Preserve default OFF or park the experiment if the measured
benefit is absent or offset by preparation/dispatch overhead. No GPU, browser,
private account, server or public operation was run by this worker.
