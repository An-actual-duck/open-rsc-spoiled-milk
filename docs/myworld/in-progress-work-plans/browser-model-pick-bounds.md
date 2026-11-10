# Browser frame-local projected model picking experiment

Status: adopted for full GPU-only browser ownership after the private browser comparisons below. Native/software/mixed ownership remains excluded.

The existing Scene tests projected bounds per eligible resident face. The internal `spoiledmilk.browserModelPickBounds` property now defaults true. Private benchmark controls deliberately default OFF for reference comparisons; ordinary/software URLs cannot supply an override. When enabled AND full GPU-only browser resident ownership is actually active, Scene computes the model's resident ownership once and a fresh projected model mouse envelope before its face loop. A rejected envelope can omit an individual face's original projected-coordinate scan only after confirming all face indices refer to this rotation's used vertices. Inside/unproven envelopes retain the original face helper.

No rotation, polygon raster/pick, near-plane clipping, interaction-only pre-detection, picking order, anchor or GPU geometry is removed. The default/native/software/mixed-ownership code retains the previous helper. Bounds have no cross-frame/model cache. Every invocation rereads the current projected coordinates, viewport offset and pick scale.

Conservativeness:

- X uses the exact projected ints. Y first performs the original int viewport addition, including wrap, before building its envelope.
- A face's min/max lies within the model's min/max when all its indices lie below the current rotated vertex head.
- Scale1 uses the original two-pixel padding. Expanded scale uses at least14 pixels and the model span×(scale−1)/2 rounded outward in double arithmetic. The old wrapped int product's nonnegative padding cannot exceed this mathematical nonnegative product. Double is exact in the useful bounded range; larger products create envelopes covering every int mouse coordinate. No unbounded pick scale is clamped.
- Original lower-bound int underflow can only raise that bound relative to its mathematical value; upper-bound overflow can only lower it. An accepted original interval remains within the outward mathematical envelope.
- Null/short projected arrays or invalid vertex heads return an unproven envelope. Malformed/null/short face indices, negative indices, and valid spare-capacity indices bypass rejection and execute the untouched original helper, retaining its result/exception behavior.

First CPU proof freezes actual Scene source at `2b0681f841298d840ca13ee0ebb5a2ec7d9f6888`. FrozenOFF, frozenON, currentOFF and currentON each render576 real headless Scene frames with one identical ordered pick/anchor/retained-face/resident-mesh/software-pixel SHA-256 digest (`04a53a9d5a6d6623859a3609185c22e703e5030a08f4630e57134d03bab56ed4`). Cases cover a dense mutable mesh, early/late cull, GPU-only/mixed/CPU-depth/native ownership, forced legacy, mutable transforms/kinds/topology/materials, expanded scales throughInteger.MAX_VALUE and near-plane camera positions.200,000 direct projected integer cases pass400,018 checks, including25,550 accepted original faces and46,575 rejected models plus exact malformed/spare-capacity fallback results/exceptions. Existing1084 Scene ownership and2975 wall/roof fixtures also pass.

Initial evidence: `output/tests/browser-model-pick-bounds/20261110T034231097031Z`. The final maintained suite also compiles the actual private Bootstrap against the freshly compiled Scene, validates requested versus actual enabled receipt and ordinary/software policy isolation, and uses isolated source-only work counters. No production per-face timing/counter is added. A model scan and per-face index validation can cost more than the old scan for small or mouse-over models; matched actual browser measurement is required before adoption. The preceding profile's face-helper cost was approximately0.55ms/frame, so possible gain is bounded and60 FPS is not implied.

Private benchmark-only startup controls are independent: `modelpickbounds=on|off` maps to `spoiledmilk.browserModelPickBounds` (defaultoff); `halomatch=on|off` maps to `spoiledmilk.presentationHaloMatchReuse` (defaulton). Ordinary/software URLs set neither property. Requested values are exposed in `browserProof.benchmark.modelPreparationRequested`. A one-time benchmark observer reports the actual model-pick Scene getter separately in `browserProof.modelPickBounds`; existing reportPreparation ABI is unchanged. The halo flag is only a harmless relay here; its World implementation and acceptance tests belong to the manager's separate task.

## Final CPU review evidence

`output/tests/browser-model-pick-bounds/20261110T034846893960Z/result.json` is the complete fresh CPU receipt. All four576-frame digests match. Both existing1084/2975 suites and200,000 conservative integer cases pass. Isolated instrumented source copies (never production) preserve that same digest and count41,628→36,278 original face-bound helper calls, with584 model scans,182 rejected model envelopes and5,350 guarded face rejections. This proves the new branch is exercised, not its runtime speed: model scanning and index guards remain added work. Actual browser A/B is required.

An earlier fixture iteration accidentally compiled the unrelated private server source with the client-only classpath; that failed during Bootstrap build after the Scene proofs passed. The runner now compiles exactly `tools/browser-gameplay/src/orsc/browser`, matching the maintained preparer; the final complete rerun passes and the failed iteration remains preserved.

`build-client.sh` passes. The old b503 worker JAR was preserved under `output/tests/browser-model-pick-bounds/preserved-client` before building this source. Query-control and actual frontend VM tests pass, including `modelpickbounds`/`halomatch` independent defaults and no ordinary override. No GPU, browser or public-server process was launched. Default remains OFF pending manager review and measurement; no performance claim is made.


## Measured default adoption

One frozen client artifact (source dc86bccc9adea0610e8bc0d5bcdf28abb365fa2e,
SHA25643c316df878f315c8335f594ea7877083f011f4afdd5db6697d415264ef8ccbc)
ran separate fresh Java8 sessions:60seconds warm,40seconds timed each, internal960x540,
fixed location/camera/daylight, owned GL+stream validation+halo comparison reuse ON.
ModelOFF56.13799FPS/32.3438 mean actor composites; modelON59.70564/30.5012;
reverse modelOFF58.19356/31.2096. Population and warm/system variation prevent
claiming the entire raw difference as isolated speed. The ON frame intervals were
also lower within shared31/32/33actor bins than both OFF runs. All runs retained
401color draws,477928triangles and101/101ready chunks; requested/actual Scene
receipts matched. No timed screenshots/profilers/input or worker CPU tests.

This supports adopting the proven conservative work reduction, with a measured
near60FPS result for this private scene. It is not an all-device, fullHD, maximum-
distance or busy-scene guarantee. Private benchmark still defaults OFF and supports
explicit on/off; no player-facing setting is added. A fifth real Scene cohort now
clears the property entirely and checks the actual default browser gate, while
retaining native/CPU-depth/mixed exclusions and the same frozen digest.
Evidence: manager output/reviews/browser-display-performance/benchmarks/
halo-model-off-on-1, halo-model-on-on-1, halo-model-off-on-reverse-1. Public unchanged.
