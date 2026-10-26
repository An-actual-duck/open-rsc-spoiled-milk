# Coordinated next-update staging

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
- Mark incomplete components and unrun acceptance explicitly. A prepared map is
  not proof that every tower restriction or pending weapon coating is finished.

## Tower candidate additions

Six stationary, nonattackable/nonaggressive associates are added to the preserved
34358-placement decorated checkpoint, bringing the NPC count4047→4053. Their
rank-bound entrance/upward-stair coordinates and dialogue behavior are documented
in `slayer-tower-stair-guard-migration-review.md`. Downward travel, room doors and
exit from the tower remain unrestricted. Existing rank progression and completed
mandatory tiers are preserved by the established Slayer migration rules.

## Eventual migration sequence

1. Complete source/content changes and maintain a matching staged editable map.
2. Build paired server/client artifacts from the exact reviewed Core revision;
   validate map terrain/materials, definitions, placements, NPC capacity/reserve,
   guard bindings and the relevant progression/combat/content gates.
3. Run an isolated private session using that exact revision and map. Obtain
   owner gameplay/visual acceptance of tower routes, NPC behavior and final
   content; record known exclusions and preserve the accepted candidate.
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
