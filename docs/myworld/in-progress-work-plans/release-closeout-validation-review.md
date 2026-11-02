# Release checklist and supplemental validation closeout

Source base: `807afc243ca497896a60c97183283980af7a8cbd`.
Worker: `fix/release-checklist-and-validation` in Core ai-2.
Status: focused implementation and tests complete; exact READY commit is recorded
by the worker handoff. Manager integration and final combined review are separate.

This branch changes documentation and supplemental tests only. It does not
change gameplay, numeric balance, configuration, task data, map selections,
artwork, accounts, staging or public processes. The separate tower-task and
Deathwing/progression/contact-route corrections remain in progress until their
own exact handoff is reviewed and integrated.

## Current release record

`next-major-release-goals.md` is the single current closeout checklist. Roadmap,
migration and effect-standardization summaries link to it. The active summaries
now agree on twenty spells (nine blessings, all nine curses and two Unify
spells), eighteen selected cumulative god-spell animations, twenty approved
icons and all fifteen coatings. The approved glove/no-worn-monocle choices are
closed. Tier 4, Mystic/Fierce/Corporeal bosses, Ante and the broader status overhaul
remain deferred. Dated implementation receipts and older illustrative proposals
are preserved as history rather than relabeled as current requirements.

The checklist explicitly requires actual prepared tower-task configuration,
Deathwing assignment, versioned existing-player progression, final combined
regression/static review, an ordinary-player walkthrough and owner acceptance.
A fixture forcing a flag is not evidence that the shipped configuration enables
it. Source completion, map selection, QA, acceptance and live activation remain
distinct. No reviewed worker correction is described as already integrated.

## Five repaired supplemental gates

All five failed unchanged at the source base. Original return codes and logs are
preserved under ignored `output/release-closeout-validation/baseline/`.

| Gate | Original failure | Focused repair and preserved constraints |
| --- | --- | --- |
| `test-npc-elemental-magic.py` | Whitespace-sensitive NPC `dualElementProcs` builder text | Match whitespace independently while preserving the exact Startle/acid/**zero Frostbite**/Splinter arguments. No proc or eligibility requirement removed. |
| `test-animation-asset-migrations.py` | Expected 34 custom projectiles; current canonical range is 35 | Shared source check derives the full custom range from the canonical wire enum, verifies unique IDs/native boundary and client/server IDs 39–41, preserves 36 frame slots and 16-bit sprite bounds. Existing geometry, archive and animation assertions remain. |
| `test-equipment-proc-animation-migration.py` | Same stale 34-projectile count | Uses the same canonical contract check; all individual weapon/armor animation, selector, size and asset assertions remain. |
| `test-fire-sword.py` | Expected retired Earth percentage-speed API | Require the dedicated Earth-sword case to apply 10 shared Slow power with `CAP_ONE` (12), and reject the retired adapter there. Existing damage, chance, eligibility and drop checks remain. |
| `test-slayer-movement-preview.py` | Expected complete definition isolation after intentional Banshee undead overlay | Permit only the exact `NpcDefsMyWorld.json` record `{id:865, undead:true}`. Reject duplicates, unknown fields/overrides and every other overlap. Verify overlay load order; retain manifest hashes, PNG geometry, native frames, pose IDs 78–85 and both disk/JAR fixture runs. |

`animation_contract_checks.py` is a small shared test helper, not a production
contract change. Additional in-memory counterexamples confirm that count 34,
duplicate wire IDs, server/client ID disagreement and a shifted native boundary
still fail. No original PNG/archive receipt was regenerated or weakened.

## Validation

All commands run offline in the worker, with heavy jobs serialized by the Core
lock. Matching client/server builds use Java 8 and 512MiB SerialGC; the native pose
fixture requests 256MiB. No app/server/client session was started.

- Matching Java 8 client and server builds: PASS.
- All five repaired scripts: PASS to completion.
- Existing `test-projectile-animation-fallbacks.py`: PASS, including allocated
  range/non-overlap checks.
- Native Slayer fixture executes twice, from source and embedded resources:
  eight previews, 120 source frames, all directions, pixel fidelity, cadence/idle,
  Dark Beast 78–81 and Abyssal 82–85 pose isolation PASS.
- `ant test_slow`: PASS, including shared pool/lifecycle/HUD/cadence boundaries
  and real Earth Dragon/KBD/sword/Ogre damage/RNG characterizations. The existing
  fixture-loading `Broken Npc` diagnostics are retained; this target does not
  establish final map/spawn acceptance.
- Changed Python Ruff check: zero findings; byte compilation PASS.
- `git diff --check` and current local document-link checks: PASS. Earlier dated
  release-checkpoint anchors remain available for existing links.

No production Java changes require a new whole-program analysis in this worker.
The manager must run compiler/static analysis against the **final combined
commit and rebuilt artifacts**, after both focused fixes integrate. Prior
incomplete SpotBugs output is not a pass.

## Final analysis run configuration

Do not repeat the earlier 512MiB whole-program analyzer attempt or stop a healthy
analysis after four minutes. The verified PMD launcher honors
`PMD_JAVA_OPTS='-Xmx1536m -XX:+UseSerialGC'`. The verified SpotBugs launcher
supports `-maxHeap 1536` and its `maxheap=1536` environment override. Inherited
`_JAVA_OPTIONS`/`JAVA_TOOL_OPTIONS` heap limits can override those values; remove
conflicting heap settings for the analysis subprocess. Keep Java 21 tools,
adequate memory, one heavy job and a bounded window of roughly 20 minutes.
The normal Java 8 build/test bounds remain separate.

This identifies configuration only; no whole-program CPD/SpotBugs review was
run or claimed by this worker. Investigate each new finding on the final code
and preserve exact commands, versions, hashes, logs and any genuine limitation.
