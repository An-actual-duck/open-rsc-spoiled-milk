# Browser explicitly owned GL frame state — review candidate

This focused browser-only change removes state capture/restore queries inside an exclusively owned renderer frame. Standalone sky, shadow, coverage, retained-frame and sprite-atlas APIs retain their existing scoped behavior by default. No GL context Proxy, Java change, shader approximation, error-poll removal, visual-quality change or public-server operation is included.

The owner establishes default READ/DRAW framebuffer, exact viewport, full color/depth write masks, disabled scissor/stencil/rasterizer-discard/cull/blend/polygon-offset/sample caps, enabled dither, texture unit zero, and baseline blend state before the shadow pass. Child passes explicitly establish their own state. Retained FULL-to-BASE replay alone temporarily owns a nondefault DRAW framebuffer and returns to the default target. World color establishes depth state/program/VAO/materials; the sprite pass establishes its state; opaque normalization uses the known frame clear/masks; the final coverage/marker pass establishes its state. Owned mode never claims to restore external caller state.

Ordinary WebGL now selects the verified owned pipeline by default. Software/native paths remain unchanged. Private WebGL `?benchmark&ownedstate=on|off` still defaults off for matched measurement; ordinary URLs ignore this override. Benchmark sessions additionally expose `window.browserBenchmarkOwnedState(boolean)`: requests queue until the END of the existing native `frameWork` callback, after a complete frame's uploads, shadows, world and final UI. Every presented frame snapshots the selected policy; bounded `worldFramePolicies` and `worldFrameSpriteGroups` align with `worldFrameSamples`; transition receipts record the frame boundary. No extra native calls or timer per draw are added. Normal sessions expose no live switch. Stop clears it.

CPU milestone: `node tests/myworld/test-browser-owned-gl-state.mjs` passed 42 deterministic full pipeline frames / 568 effective draw, clear and retained-copy operations, including continuous live policy alternation. Scoped and owned state/resource/draw streams match; 2,766 scoped hot state queries versus zero owned queries in the instrumented context (the real GPU timer disjoint query and cold capability queries are deliberately retained); GL error-poll counts match. This is an instrumented state oracle, not rasterized pixel or FPS evidence. It includes coverage RGB/T, all fade counts 1–16, duplicate and BASE replay, sky layers, near/far shadows, ground sprites, resized/underground/opaque-reset frames, context loss, partial upload errors and idempotent resource disposal.

Existing default scoped sky, shadow/lighting, coverage, retained, atlas, resident-cache, benchmark-query and frontend VM contracts pass. The manager ran the frozen actual-production-shader/native-sky GPU differential successfully; exact results follow below. Throughput and 60 FPS are not established by these functional tests.

Prepared manager-only hardware proof:

```sh
python3 tests/myworld/prepare-browser-owned-gl-hardware.py --prepare-only --run-id UNIQUE_RUN
node tests/myworld/run-browser-owned-gl-hardware.mjs --run-hardware ABSOLUTE_PREPARED_RUN
```

The preparer freezes original `9e570ad332a3e641238acb0aa646b9884b3aa23a` modules and the candidate modules, extracts actual current production world/depth shaders, and exports shared native sky geometry plus day/sunrise/night palettes. All staged files have SHA-256 receipts; the dependency JAR is recorded (`b5035079673342899046b7c498f6e34f8c4a26d7047ff40307006095a7b1fb16` in the current run). The explicit runner serves only the frozen allowlisted files on loopback with a dedicated Chrome profile and requires physical AMD ANGLE/OpenGL. It compares original/default-off/owned-on/live-alternating frames, then checks Stop during async shader initialization and actual WebGL context loss. GPU readback is functional evidence only.

The first frozen v2 hardware attempt failed BEFORE pixel comparisons because the fixture declared zero sprite groups despite committing one. That declaration is corrected to actual 1/1 required/uploaded groups in checkpoint `ec8f133eadef625342f44d70b76d3fe1d175350a`; the CPU fixture now asserts initial source completeness, and hardware diagnostics record declarations, created source IDs, uploaded geometry, actual sprite counts/mask bytes and readiness. Old failed evidence remains unchanged. Fresh stage `output/tests/browser-owned-gl-state/hardware/checkpoint-pipeline-v3` was executed by the manager and passed on physical AMD RX 6700 XT via ANGLE/OpenGL4.6. Across42 cases and42 atomic live transitions, original/default-off/owned-on/live-alternating rendered12,976,128 compared bytes with0 changed bytes and3,021,617 nonzero RGB components. All four sessions reached ready with all3/3 chunks resident. Actual context-loss blocking and Stop during asynchronous shader initialization also passed. The frozen hardware-result.json and console events preserve the full receipt.

For same-warm-world measurement, clear `worldFramePolicies` and `worldFrameSpriteGroups` whenever externally clearing `worldFrameSamples`; those three arrays push/shift together internally. Do not attribute population-changing cohort FPS to this change without a matched or alternating policy comparison. Current 60 FPS goal remains unmet until measured otherwise.

`workspace-layout.py check` still reports inherited unclassified output-root directories; this change creates no new root-level category. All new fixtures, logs and preserved stages reside under declared `tests/browser-owned-gl-state`.

This isolated functional proof covers production world/depth shader replay, three shared native sky phases, ground sprites, coverage C/T, fade counts1–16, duplicate/BASE replay, resize/underground/reset paths and ownership transitions. It is not a complete gameplay test, browser/device matrix, public deployment or throughput benchmark. The manager subsequently completed matched same-warm-world gameplay measurement, documented below; this supports making owned mode the ordinary WebGL default. The current browser performance target remains60 FPS.

## Same-warm-world gameplay result and default adoption

Manager evidence: `output/reviews/browser-display-performance/benchmarks/owned-state-live-1/result.json`, its four `segment-*-before.json` / `segment-*-after.json` / `segment-*-population.json` receipts, `after-paired-phase.json/png`, and driver `output/reviews/browser-display-performance/owned-live-benchmark.mjs` in the manager checkout.

The immutable8132 artifact uses frontend `80b04ae256799b1c27648c8d01d348b33d2836ac`, Java baseline `e696adeee737dfdd36a9f7d3b500f1d5283f7feb` / JAR `b5035079673342899046b7c498f6e34f8c4a26d7047ff40307006095a7b1fb16`, CheerpJ5 Java8, physical AMD ANGLE/OpenGL, internal960×540. After60s warmup, four20s segments alternate OFF/ON/OFF/ON in one running world via queued frame-boundary policy changes. No source profiler was active.

| Policy | Fresh frames | Elapsed | Weighted world FPS | Mean actor groups |
| --- | ---: | ---: | ---: | ---: |
| Scoped OFF | 2,030 | 40.199s | 50.49877 | 30.96207 |
| Owned ON | 2,205 | 40.2268s | 54.81420 | 30.93469 |

Individual OFF segments:50.48748 and50.51008 FPS. ON segments:54.96665 and54.66192 FPS. JS submission fell from approximately2.71ms to1.516ms. All segments keep401 color draws,477,928 triangles,101/101 chunks ready and the same visual/settings flags. Near-identical weighted actor populations and repeated reversals strengthen attribution to ownership rather than population drift. This is a genuine measured improvement of approximately8.55% throughput on this host;60 FPS remains unmet. It is not a browser/device-wide guarantee.

Adoption changes only policy selection: `browserOwnedStateBenchmarkPolicy('webgl', ordinaryOptions)` returns true; non-WebGL returns false. Benchmark default remains false with explicit on/off and existing live queue. Ordinary `ownedstate=off` or invalid values cannot override the selected default. The frontend VM fixture verifies the actual constructor receives ownedState=true and benchmarkControl=false, the requested/selected receipt agrees, and no live control is exposed. All module standalone/scoped defaults are preserved. The already-passing hardware ON implementation is unchanged.
