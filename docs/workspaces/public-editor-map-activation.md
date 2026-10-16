# Reviewed editor maps in public releases

The hosted runner consumes the **paired installed map selected by the exact
published release**, not a regenerated historical map or a package from another
checkout. This change does not deploy unfinished gameplay from development main.
Loader-only release assembly remains a separate reviewed source operation.

## Owner workflow

1. Use an isolated authoring copy of the reviewed release with the toolkit.
   Save and review the proposed import there, with its server stopped. Never
   edit the public live checkout with the toolkit.
2. Review the resulting map-only source delta, both content-addressed package
   roles, `server/world-builder-configs/primary.json`, and the matching client
   `Client_Base/world-builder-configs/installed-client.json`.
3. Commit and publish that exact reviewed source, run matching client/server
   and full effective-definition/material/placement/collision/capacity gates,
   then prepare and publish an exact release/tag. Keep gameplay definitions,
   approved art and the live database unchanged unless separately authorized.
4. The manager deploys the exact release only while the public server is safely
   offline. Existing database-backup, clean detached published-release, port,
   process, provenance and external database-link guards remain mandatory.
5. Hosted startup validates the selected pair again and lets maintained startup
   choose `installed-primary`. It does not export an old package/hash/profile
   that would override a newly reviewed map. A subsequent reviewed map-only
   release repeats this workflow; it cannot silently revert to the old map.

No dirty-tree launch exception is added. A direct live-checkout import remains
unlaunchable until independently reviewed and promoted through release source.

## Selection and release closure

`scripts/installed-map-selection.py` checks exact bounded selector schemas,
duplicate JSON members, active layered selection, contained non-symlink paths,
both complete package inventories, matching fingerprint addresses, manifest
bytes, and client profile identity. Fingerprint and manifest hash are **different
identities**: the existing package is fingerprint `f699e5...`, manifest `f34895...`.
The generic package-check report may also contain its own normalized semantic
fingerprint; it is not substituted for the exact-byte address.

`layered_world_selected_pair` uses the existing independent generic package
parser and repeats exact selection/inventory checks afterward. These checks
establish release input closure, **not** permission to populate or run a map.
Maintained runtime structural/material/definition/transport/collision/capacity
validation still executes against the release's actual definitions. No fixed
content IDs, counts or product roster grants that permission.

Player archives validate the same paired inventory. The layered archive copies
the selected bytes and records their actual dynamic identity and source commit;
promotion refuses a dirty or changed source. Ordinary main packaging still
requires clean published main. Explicit fixes-only packaging restores the
previously reviewed `9e334b0da` source checks: exact clean pushed topic, approved
baseline ancestry and no unfinished Git operation. Its worker census now also
permits a registered, clean detached **IDLE** slot only at published main; active
or ready topics still require exact remote backup. Missing/stale/foreign state
and manually detached active work refuse.

The historical pinned bootstrap generator remains available for its original
offline/absent-selector use; present malformed metadata never falls back.
Deployment's ordinary install/require functions require the selected pair.
Explicit `run-hosted-release.sh --pinned-rollback VERSION` remains distinct from
normal installed mode. Existing legacy-map rollback is also explicit; neither
is an automatic recovery from malformed input.

## Launch attestation and limits

Installed launches record `production-installed`, actual package path/hash and a
bounded selector/inventory digest without exporting source overrides. Status
rechecks that exact selection with no JVM or build. Old pinned-production and
legacy-rollback status checks remain separate. Inherited source-selection
environment/JVM overrides refuse normal installed mode rather than silently
changing authority.

New isolated tests cover repeated edited selections, mismatched roles/profile,
malformed metadata, duplicate controls, symlinks, parser-time source drift, exact
copy closure, clean promotion, marker drift, inherited source pins, and actual
registered IDLE/topic state and backup checks. Existing player packaging fixtures
now supply a real fingerprint-addressed paired selector rather than a fake
client-only address. Runtime gameplay acceptance, publication and activation are
manager gates, not performed by the worker's selector tests.

Worker verification: all 11 new selector/orchestration/census tests pass, as do
`test-player-release.py`, `test-layered-production-promotion.py`,
`test-hosted-server-safety.py`, `test-published-release-identity.py` and
`test-hosted-alpha-config.py`; shell syntax and `git diff --check` pass.
The actual maintained pair passed the generic Java package parser and exact
copy/promotion verification with JVM heap capped at 768 MiB: 1789 files per role,
1782 sectors, exact-byte fingerprint `f699e5a37709a0f127d676d91f07f64e256825bef6d65a7a4ccd13c8310f3b75`,
manifest `f3489503d2c323e669ab3004345271c6137a58698cc8c0bf0b0fb2587eb52b29`.
The actual three registered worker states also passed the new census using the
unchanged release-9e AI-common APIs, without writes/state changes. Temporary
fixture Git repositories/remotes and promotion output were tool-owned only;
no public runtime, deployment, release publication or database was touched.
