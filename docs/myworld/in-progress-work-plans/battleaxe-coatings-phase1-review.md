# Battleaxe coating Phase 1 review package

2026-10-25. **Review-only; Phase 2 remains blocked on explicit gameplay/design feedback.**
Branch: `feat/battleaxe-slow-poison-coatings`.
Starting HEAD: `ed8b55bfa94b4d2471b8421f0ba68c8ea2dbe0fa`.
Initial pushed proposal: `25444ea5768f920e40a215d84e17c310398e700f`.
Proposal: [battleaxe-coatings-design.md](battleaxe-coatings-design.md).
Final exact pushed review commit is supplied by the checkpoint/READY record and
worker response, avoiding a self-referential commit hash in this file.

## Scope and deliverables

No production gameplay, definitions, item IDs, asset archive or generator input
has changed. Approved but release-separated antidote work was audited, not
reimplemented. Core owns integration and private gameplay acceptance.

Reproduce independent evidence:

```bash
python3 scripts/audit-battleaxe-coatings.py
python3 scripts/audit-battleaxe-coatings.py --output output/battleaxe-coatings-review-repeat
```

Review outputs:

- `output/battleaxe-coatings-review/review.html`: self-contained browser sheet,
  embedded inventory/dose/held images, no server or network dependency.
- `output/battleaxe-coatings-review/coverage.json`: merged before-state
  definitions, client inventory masks, held palettes, source/output SHA256s,
  all 1,002 custom archive-entry hashes and held geometry.
- `output/battleaxe-coatings-review/SHA256SUMS`: hashes of all generated files
  (including local PNGs, portable HTML and coverage data).
- `output/battleaxe-coatings-review/battleaxe-matrix.csv`: all 15 base items,
  requirements, offenses, legacy aim/power, prayer, speeds, values, palette and
  appearance mappings, proposed counterpart names and exceptions.
- `inventory-contact.png`: all 15 base/candidate inventory pairs at nearest4×;
  the HTML also shows each at native48×32.
- `dose-contact.png`, `coating-{3,2,1}-dose-candidate.png`: isolated bottle
  concepts, no active dose IDs.
- `held-{ID}.png`, `held-{ID}-mirrored.png`: 18 base/candidate frame pairs for
  each of the 15 weapon palettes, plus geometric mirrored previews.

The script, report, matrix, coverage and portable HTML are the durable review
package. Individual PNG exports are reproducible local diagnostics. None are
installed as production assets. Before/after production weapon definitions and
archive bytes are identical; proposed variants have **no allocated IDs**.

## Coverage findings and technical constraints

All 15 IDs: 89,90,91,92,93,205,429,2002,2013,2024,2035,2156,2752,3118,3269.
The actual roster adds Exalted Rune to the handoff examples. Every other
axe-shaped item remains excluded. Name matching discovers the roster, then
explicit audited tier classification rejects an unreviewed roster change.

- All inventory items use `items:84` except Dragon's `items:272`. Native48×32,
  occupied bounds5,1,39,30 or Dragon5,1,41,30 are preserved. Controlled blade-edge
  candidate changes40 pixels on regular icons and51 on Dragon; alpha, canvas,
  silhouette and handle colors remain unchanged.
- Held `equipment:battleaxe` has18 frames, MAIN_HAND layer3. Walking/directional
  frames0–14 have64×102 declared canvases; attack frames15–17 have84×102.
  Preserve these different native canvases; do not normalize to inventory scale.
  Captured crop/shift/offset and frame hashes are in coverage JSON.
- All15 held palette passes include all18 frames and mirrored review variants.
  Grayscale blade edges alone receive teal, preserving colored handle pixels
  and every transparent hand gap. This is an asset-plane preview, not proof of
  body/hand occlusion during a rendered gameplay attack.
- White2156/grey3118 reuse black appearance115; current held303030 differs from
  their inventoryE7E2D6/9EA59F. This is faithfully shown and not silently fixed.
- `Equipment.battleaxeIds` includes neither grey nor Exalted, so their current
  family high-roll bonus differs from other battleaxes. Coating implementation
  must preserve base behavior unless Core approves the focused base correction.
- Dragon2752 has Hero's quest equip gating when bypass is off. A new catalog ID
  must route through the same gate rather than passing only skill requirements.
- No intrinsic uncoated Titan2024 poison producer was found in current weapon
  poison helpers. Preserved85ed0e8a PoisonPower also requires a POISON-prefixed
  material name rather than recognizing base2024. Core should supply an exact
  alternative approved producer if the release build contains one. Do not infer
  one from material naming. The approved antidote commit fixes Titan's existing
  poisoned ammunition/spear material resolution; that is a separate issue.
- Current runtime catalog test reports IDs0–3399. Historical antidote3400–3407
  are above that snapshot, but still require coordinated allocation/revalidation
  at implementation; this review does not reserve them.

The first isolated mask incorrectly selected orange handle edges as red metal;
it was corrected before this package. Final review confirmed the visible handle
is retained. Dark/teal readability, Dragon palette and skull bottle identity still
need owner visual feedback. Static tint follows legacy grayscale arithmetic;
there are no renderer screenshots or equipped character composites. No existing
offline equipped compositor was used, and no game client/server was launched.
Mirroring does not certify camera-dependent frame selection or layer ordering.

## Independent validation

All offline jobs ran sequentially with Java8
`/home/justin/jdk8u482-b08`, `ANT_OPTS=-Xmx768m`,
`JAVA_TOOL_OPTIONS=-Xmx512m`. Use the repository's bundled Ant: plain `ant` is
not installed on this execution environment.

```bash
# From server/
JAVA_HOME=/home/justin/jdk8u482-b08 \
PATH=/home/justin/jdk8u482-b08/bin:$PATH \
ANT_OPTS=-Xmx768m JAVA_TOOL_OPTIONS=-Xmx512m \
bash ../tools/vendor/apache-ant-1.10.5/bin/ant test_slow test_combat_strict

# From repository root
python3 tests/myworld/test-poison-model.py
python3 tests/myworld/test-npc-poison-death-lifecycle.py
python3 tests/myworld/test-equipment-animation-id-contract.py
python3 tools/generators/generate-item-overrides.py --check
JAVA_HOME=/home/justin/jdk8u482-b08 \
PATH=/home/justin/jdk8u482-b08/bin:$PATH \
ANT_OPTS=-Xmx768m JAVA_TOOL_OPTIONS=-Xmx512m bash scripts/build-client.sh
JAVA_HOME=/home/justin/jdk8u482-b08 \
PATH=/home/justin/jdk8u482-b08/bin:$PATH \
ANT_OPTS=-Xmx768m JAVA_TOOL_OPTIONS=-Xmx512m \
python3 tests/myworld/test-client-runtime-item-definitions.py
```

Results: shared Slow passed (103 boundary assertions plus producer, legacy NPC
ranged and scheduler/HUD/lifecycle coverage); strict combat144/144 passed.
Current Slow fixtures retain the eight Slayer enemies' relevant cadence
coverage. Poison model, NPC poison death lifecycle, stable equipment animation
IDs, generator check (2049 overrides), client build and runtime client catalog
(3400 definitions, IDs0–3399) all passed. These are **unchanged baseline checks**,
not verification of a coating implementation or private gameplay acceptance.
No current antidote fixture exists in this checkout; its preserved focused
fixture must be adapted when the approved prerequisite is reconciled.

Candidate exporter validates15×18 held frames,15 inventory pairs,3 doses,
alpha/bounds preservation and unchanged source archive hashes. Two separate
exports compare byte-for-byte for every generated output. The portable HTML
was rendered offline in headless Chrome at1440×1200 and inspected alongside
the PNG inventory/dose/held contact sheets. All91 embedded images are internal
PNG data URLs. No paid service, tool install or external source was used.

Initial attempts: plain Ant unavailable; switched to bundled Ant. Initial
sandboxed parity build could not write existing build output, then passed with
worker access. Initial sandboxed Chrome hit a restricted socket operation,
then rendered successfully with authorized local access. These were environment
issues, not baseline gameplay failures, and no tests were weakened.

## Fixtures to implement only after design approval

This is coverage planning, not a claim that these tests or behaviors exist.

| Production path / likely fixture | Required cases |
|---|---|
| Recipe plugin + Herblaw completion fixture | Approved level−1/level boundary, herb/water/Eye/Gland exact atomic consumption, quantities, yield, XP and all-or-nothing missing ingredient/full inventory; preserve both1/128 drops |
| Coating item-use plugin | All15 explicit mappings; notes/equipped/ordinary hatchets/quest objects reject without loss; final-dose vial; removal/recoating/cross-poison behavior only as approved |
| Runtime catalog/equip fixture | Server/client appended IDs, name/description/value/requirements/stat/speed parity; two equip modes; Dragon quest gate; white/grey prayer family; source/base mapping retains any family biases |
| PvmMeleeEvent resolved-hit fixture | Zero, blocked, positive, lethal and surviving hits; one primary Slow application; no scythe secondary/Naga offhand/poison/reflection proc; no polling/readiness repeat; unchanged RNG order for unrelated weapons |
| Existing generic poison / prerequisite antidote fixture | Exact proposed payload only after approval; dynamic weapon proc success/failure/recharge; weapon/armor aggregation; no double Titan producer; retained cap/provenance; strongest antidote and additive cleanse, zero clamp, dose/HUD/recipes |
| Slow reconciliation fixture extension | Actual attack tick intervals and modifiers; neutral3-tick proposal reaches tier1 on hit3; misses;4/6/8-tick cadence;4-tick decay boundary callback order; stronger-cap retention; multi-source top-ups; HUD subthreshold and tier changes |
| Lifetime and scheduling fixtures | Death/logout/respawn/session replacement; transient Slow versus durable poison; committed deadlines cannot shorten or repeatedly move; retarget/event replacement; ownership and kill-credit policies |
| Generator/archive fixture | Regenerate explicit variant source reproducibly; parity for all15, no hatchets; inventory/held appended slots; decode all direction/attack frames; source hashes and every unrelated archive entry unchanged |
| Core private acceptance (not worker-owned) | Real mixed-source poison/Slow sustain, meaningful low-tier behavior, bosses/Slayer encounters, equipped occlusion/hand gaps/layer order, visual readability and recipe economy |

## Pending decisions and handoff meaning

The seven decisions remain exactly those in the proposal: recipe/economy;
permanent forms/application/removal; Slow4/cap12 policy; half-tier poison with
explicit low-tier threshold limitation; all15 plus base-family/quest checks;
Titan effect lineage; teal art acceptance. No feedback has been received that
approves any of them. Independent work is complete. **READY means this Phase1
review package is ready for Core review; it does not mean gameplay Phase2 is
implemented, accepted or ready to integrate as a feature.** Continue this same
branch only after Core relays explicit decisions. The broader post-Slayer
standardization reminder remains open.
