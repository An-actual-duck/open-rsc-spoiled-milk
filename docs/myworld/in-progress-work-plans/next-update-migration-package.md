# Coordinated next-update staging

## Final owner-approved map — November 2, 2026

The final source selection now matches the owner-tested private map: owner
checkpoint4855 with the approved one-tile diagonal-corner correction in
derivative4856. Owner overall gameplay and the Ichor correction are accepted.
This supersedes the older e343 selection described below; earlier exports and
the original editable save remain preserved.

- Fingerprint: `567449d3590b4e1b197a11e517d928a316ba4e92b4322d2526ca6f66455541ae`.
- Manifest: `55fae68af8e8e7e606708876f9cde5e54c2667c84562f0dd2a12528b202a4323`.
- Exact paired package copies come from
  `/home/justin/.local/share/ichor-corner-20261102/export/`.
- All566 runtime/route checks pass, including every admitted approach to both
  Crafting Guild cave ladders. The repair changes only the ground colour at
  (105,675,-1),70 to198; elevations, walls, collision, liquid and placements
  are unchanged relative to owner4855. The actual packaged-client terrain
  producer reproduces the old green and verifies the brown replacement.
- The source selectors and server manifest requirements select these exact
  bytes. No local ports, credentials, accounts, editor catalog or save history
  are copied. Current production source includes all20 Cleric spells, all15
  coatings and wool-based removal; former deferral statements below are dated
  history, not the current release scope.

Final combined tests, analysis, exact-source packaging and migration rehearsal
are separate recorded gates. Selecting this map authorizes no public shutdown
or deployment. The release label is still an owner decision.


Owner direction, October26,2026: continue finishing the Slayer update and its
completed tower together, then migrate the reviewed update in one maintenance
window. Preparing this folder does not authorize updating or restarting live.

Working package: `/home/justin/spoiled-milk-next-update`.
The folder is local release staging, not the active server, not a second manager
checkout, and not a place for unreviewed source changes. Implementation still uses
Core workers and exact READY handoffs; integration/tests use the manager workflow.

## Maintenance rules

- Keep a short root README and machine-readable update ledger. Record the exact
  source commit, server/client build hashes, map package/manifest hashes, authored
  project/checkpoint provenance, validation commands/results and pending acceptance.
- Keep source and both map halves paired in one revision. If code, definitions,
  placements or runtime requirements change, create a new candidate revision and
  rebuild/revalidate its affected pieces. Preserve the previous candidate and
  ledger; never silently overwrite its evidence or call an older test current.
- Preserve the owner's decorated editor checkpoint unchanged. Add associates and
  later final placements through a staged copy using normal project/export APIs.
  Retain the editable staged project alongside its exported packages so later
  work continues from the reviewed map, not an older exported snapshot.
- Do not put live/private account databases, credentials, keys, logs containing
  secrets or machine-specific launch state in the distributable package. Private
  gameplay sessions may use separately located disposable account copies.
- Keep the private pre-alpha editor separate from player release assets. It
  discovers server-owned content; gameplay assets/IDs are not built into it.
- Mark incomplete fixes and unrun acceptance explicitly. A prepared map or
  visible NPC spawn is not proof that task assignment, progression or ordinary
  player acquisition works with the actual release configuration.

## Source-selected private candidate — November 1, 2026

The repaired export is now copied byte-for-byte into both versioned Core map
roots, with the matching server/client selectors and required configuration
manifest/path values. It is selected for **private final acceptance**, not public
release. No owner editable project, discovered catalog, local override settings,
account database or raw logs were copied into Git. Older package directories
remain available.

- Fingerprint: `e343c03b026920ab0f6dd6e9fdd8d9601d1992177f24510f1d21a266e22e58cf`.
- Manifest: `c12686677f3bc49956af7957e1a7ec2916cffa622c4e4af8290c11a6451bacbb`.
- Each role contains 1,797 files and 46,101,722 bytes: 1,787 sectors across
  levels -2, -1, 0, 1, 2, 3, 4, 5 and 10; 34,598 placements comprise
  4,081 NPCs, 926 items, 28,612 scenery and 979 boundaries.
- The reviewed repair copy retains 64 floor-overlay corrections and three
  ground-colour corrections for accessible well/ladder routes and isolated green
  corners. Four grey cosmetic joins remain intentionally unchanged; automatic
  per-half material matching is deferred.

See [the source assembly review](next-update-candidate-assembly-review.md) for
exact export provenance, selector scope and current verification. Historical
staging/repair records remain evidence, not independent public approvals.
The public deployment remains `v0.2.89` / `a5043328f`. Provisional private
`v0.3.0-alpha.1` is not a final release label, public tag or beta decision.

The independent migration rehearsal is reviewed and integrated in manager
revision `02243c3c398cff507504969b3b77317c287a9e4b`; its disposable database
fixtures and evidence are separate from this map copy. Current source has all
20 Cleric spells (including all nine curses), 20 approved icons, 18 selected
cumulative god-spell animations and all 15 coatings. Glove art and the omitted
worn monocle visual are owner-approved. Preserve the accepted concise Cleric UI.

The November 2 tower-task and Deathwing corrections are implemented and
independently reviewed in exact commit
`9fe259238491933940f0e45a6aa75e283d14c4a6`; see the
[Slayer activation review](slayer-release-task-activation-review.md). Both production
profiles enable the tower families; the release roster is version 5 with the
Champions-tier Death wing assignments and historical-progress migration.
Refresh the paired artifacts to the final combined source and record actual
configuration/roster checks in the package ledger. Confirm all eight families,
Death wings, idempotent existing-player migration and contact/stair routes in
that candidate. A test-only flag override is insufficient; source review does
not complete private acceptance or public activation.

The [current closeout checklist](next-major-release-goals.md#release-closeout-checklist)
tracks final combined static/regression validation, paired artifacts, ordinary-
player private walkthrough and owner gameplay/visual acceptance. Boss additions,
Ante and Tier 4 remain deferred; Zamorak hostile activation is implemented
required content, not a deferral.

## Tower candidate additions

The earlier associate-preparation copy added six stationary,
nonattackable/nonaggressive associates to the preserved 34,358-placement
checkpoint, bringing its NPC count 4,047→4,053. Those are historical counts;
the source-selected package above includes the later authored work. Their
rank-bound entrance/upward-stair coordinates and dialogue behavior are documented
in `slayer-tower-stair-guard-migration-review.md`. Downward travel, room doors and
exit from the tower remain unrestricted. Existing rank progression and completed
mandatory tiers are preserved by the established Slayer migration rules.

## Eventual migration sequence

1. Complete source/content changes and maintain a matching staged editable map.
2. Build paired server/client artifacts from the exact reviewed Core revision;
   validate map terrain/materials, definitions, placements, NPC capacity/reserve,
   guard bindings and the relevant progression/combat/content gates.
3. Run an isolated ordinary-player private session using that exact revision,
   configuration and map. Exercise task acquisition/progression and production
   through normal player routes as well as combat, travel and persistence.
   Obtain owner gameplay/visual acceptance, record bounded exclusions and preserve
   the accepted candidate; development-command fixtures do not replace this pass.
4. Prepare normal player packaging/provenance checks. Immediately before actual
   migration, compare against the current public revision and fresh live state;
   never replace the live account database with a test copy.
5. Obtain fresh explicit permission for public shutdown in that maintenance
   window, back up the live database/map/configuration, and use the normal
   in-game update warning/countdown. The public-server shutdown gate in AGENTS.md
   and `docs/workspaces/live-deployment.md` remains mandatory.
6. Activate the approved paired code/assets/map through the maintained release
   workflow, retain rollback material, restart in its own terminal, and verify
   the live result. No step above is permission to do this now.
