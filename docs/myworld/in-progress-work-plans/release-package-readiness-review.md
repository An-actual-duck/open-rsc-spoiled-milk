# Release package readiness review

Reviewed November 1, 2026, in `Core-Framework-ai-1` on
`fix/release-package-readiness`, based on `0eea4c88a`.

This is an offline packaging and validation repair. No official archives, tags,
releases, source/map selection changes, public operations, account databases,
owner maps, gameplay values or artwork were changed. The manager will select
and publish the final source/map pair before building actual private review
artifacts. The provisional `v0.3.0-alpha.1` is a private rehearsal version, not
an owner decision about release maturity.

## Repairs

- **Updater version ordering and downgrade prevention.** The original shell
  updater reproducibly replaced a newer private alpha with an older public
  version, preferred a same-number alpha over stable, and downgraded stable to
  alpha. Both shipped updaters now compare numeric major/minor/patch components,
  then stable/alpha status and the numeric alpha counter. They retain a newer
  installed client. The shell implementation also replaces GNU-specific
  `sort -V` with POSIX `awk`/`sort`; digit-length keys avoid integer overflow.
  Existing download, extraction and preference preservation behavior remains.
- **Verified maintenance-release identity.** `ai-manager.sh release-check`
  previously rejected a detached live release outside main even when
  `live-status` proved its published release tag. The guard now reuses
  `myworld_git_commit_publication_state` only for that fallback. The fixed live
  path, clean/detached state, exact launch marker, local tag and remote published
  tag must agree. Missing/local-only/moved tags and dirty live trees remain
  rejected. No history is merged and no refs are fetched or rewritten by the
  check.
- **Current approved item inventory.** The player asset gate's historical list
  omitted 39 approved PNGs: gem equipment, two spider eggs, the coating and
  gland, 15 coated axes, Blight sigils and the Moonsilver family. The new fixed
  `tests/myworld/fixtures/player-release-current-item-art.json` receipt records
  their exact source hashes and existing provenance documents/manifests. Those
  entries must exist in the real rebuilt client JAR with identical bytes.
  Unexpected source/JAR additions, missing art, changed approved bytes and old
  forbidden asset directories still fail. This does not authorize all files
  found under the asset directory or refresh historical native archives.
- **Master Tanner stock test.** The stale Giant Leather expectation contradicted
  the approved Slayer material retirement. The fixture now checks the complete
  seven-entry stock and exact quantities, rejecting extra or duplicate stock.
  Gameplay stock and placement were not changed.
- **Private launcher test.** A test wrongly required the repository cache to
  already contain private port 43615, although its canonical value is public
  port 43605. It now checks the Windows launcher writers and executes the real
  shell launcher against disposable files and fake Java/Ant. The configured
  private port overwrites old cache state; the public port is refused before
  cache, seed-copy or launch mutation. No server is started.
- **Retired item preservation test.** Its old baseline predated the approved
  shared Stagger descriptions on only Ogre items 1880–1884. Their exact
  descriptions are composed from immutable reviewed commit `5d8ce1695`; all
  other fields still compare against the original retirement baseline
  `f8c794e65`. The other 30 items retain complete original-field checks except
  binding. Mutation checks reject altered Ogre text, price or binding and an
  unrelated retired-item description. Client/note binding checks remain.

## Packaging audit

The existing player packager still requires a clean published manager source,
release-build marker, redistribution acknowledgement/legal notices, Java 17+
Windows runtime and all supported OpenGL native classifiers. It copies only
explicit player inputs: embedded client art, native video/audio archives,
selected fingerprint-addressed map package/profile, notices and launchers. It
writes the chosen endpoint and source/version markers instead of copying local
UID, credentials or player settings.

The installed-map selector checks the exact paired server/client inventory.
Layered release packaging verifies player archive source commits before its
selected-package validation and promotion checks. These strict pairing gates
were preserved. Native textures/models travel in the existing video archives;
the asset regressions below independently verified current well textures,
coated native frames and real client resource selection. No additional
packager mutation was necessary.

## Verification

Heavy tests/builds ran serially under
`/home/justin/.local/share/core-heavy-build.lock`, with Java 8 and 512 MiB
SerialGC. Evidence, baseline failures, runners and logs are preserved in the
ignored `output/reviews/release-package-readiness/` directory.

| Command (`python3 tests/myworld/…`) | Result |
| --- | --- |
| `test-player-release.py` | PASS synthetic Java/Windows package extraction, exclusions, endpoint/source/pair checks, launcher fallback, real rebuilt client and exact current art |
| `test-player-updater.py` | PASS 16/16: eight real shell updater scenarios and the same eight real PowerShell scenarios |
| `test-ai-workspace-workflow.py` | PASS 27/27, including exact published fixes-only identity and negative guards |
| `test-installed-map-release-selection.py` | PASS paired inventory and invalid selection rejection |
| `test-published-release-identity.py` | PASS annotated/lightweight identity, remote drift, missing and invalid tag rejection |
| `test-private-server-launchers.py` | PASS 6/6, no server or real DB opened |
| `test-release-hotkey-guard.py` | PASS |
| `test-crafting-guild-master-tanner.py` | PASS complete retained stock, placement, trade and client definitions |
| `test-retired-leather-items.py` | PASS complete preservation, four mutation-negative cases and 35 client/note forms |
| `test-gem-dragons.py` | PASS native/shared-sheet provenance, all 18 frames, five tints and real client paths |
| `test-battleaxe-coatings.py` | PASS 15 icons, 34 Aseprite sources, native frames, preserved archives and both client modes |
| `test-practice-item-art.py` | PASS approved potion/gland receipts, Aseprite replay, packaged resources and both client modes |

`sh -n`, `bash -n`, Python compilation and `git diff --check` passed on changed
scripts. The updater cases cover private-to-public downgrade refusal,
stable/alpha ordering, equal version, numeric alpha 9/10 ordering, newer core
version, successful update with preserved preferences and unavailable API.
PowerShell used manager-provided checksum-verified portable PowerShell 7.6.9
on Linux. Initial skipped PowerShell results were superseded by all 16 passing
cases (`updater-powershell.log`).

## Remaining acceptance boundaries

These results are not native Windows/macOS GUI launch tests, actual final
release-package gameplay acceptance, public network download tests, or an
exhaustive proof of all custom content. Synthetic package/updater fixtures use
local fake HTTP responses and temporary files. Actual private packaging and
source/map promotion remain the manager's next steps; private gameplay and
owner icon selection remain separate acceptance gates. No public stop/restart
is authorized by this work.
