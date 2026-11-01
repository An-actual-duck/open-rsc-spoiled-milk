# Next-update source candidate assembly

Prepared November 1, 2026, in `Core-Framework-ai-1` on
`chore/next-update-candidate`, based on published `8aa187a14`.
This selects an already verified map copy for **private final acceptance**.
It is not owner gameplay/visual acceptance, an official package/tag/upload, a
beta/version decision, or permission for public activation. The public server
remains `v0.2.89` at `a5043328f`.

## Exact map and source selection

Both source roots contain byte-for-byte copies of the reviewed export from:

`/home/justin/.local/share/green-half-scan-20261101/export/{server,Client_Base}/world-builder/packages/e343c03b026920ab0f6dd6e9fdd8d9601d1992177f24510f1d21a266e22e58cf/package`

The maintained private staging mirror is
`/home/justin/spoiled-milk-next-update/maps/repair-preview/export/`.
Only the exported game package is copied into Git. The original owner editable
project, checkpoint history, discovered catalogs, local override settings,
account databases and raw evidence stay outside the commit. Prior versioned
map directories are retained.

| Identity | Value |
| --- | --- |
| Inventory fingerprint | `e343c03b026920ab0f6dd6e9fdd8d9601d1992177f24510f1d21a266e22e58cf` |
| Manifest SHA-256 | `c12686677f3bc49956af7957e1a7ec2916cffa622c4e4af8290c11a6451bacbb` |
| Package | `rsc-remastered.spoiled-milk-layered-world` version `0.5.0` |
| Files/bytes per role | 1,797 / 46,101,722 |
| Terrain | 1,787 sectors; levels -2, -1, 0, 1, 2, 3, 4, 5, 10 |
| Placements | 34,598 total: 4,081 NPC, 926 ground item, 28,612 scenery, 979 boundary |

`server/world-builder-configs/primary.json` and
`Client_Base/world-builder-configs/installed-client.json` select the same pair.
The only versioned configuration changes are the required package path and
manifest hash in `server/myworld.conf` and manifest hash in
`server/myworld-host.conf`. No manager-local settings were imported.

The previously reviewed copy includes 64 floor-overlay repairs and three
isolated ground-colour repairs, preserving authored placement counts. They keep
well shores/ladder returns accessible and correct demonstrated green corners.
This branch makes no further terrain or placement edits. Four grey cosmetic
joins remain intentionally unchanged because the current palette cannot match
them faithfully. The broader automatic half-tile/material system remains deferred.
The retained scan evidence covers all nine saved levels and 4,117,248 tiles;
its remaining green candidates are not presumed defects.

## Generic validator compatibility repair

The first maintained package check rejected the legitimate level-5 payload's
`layered-world-placements-v5`. The native server already supports this format:
it retains v4 NPC bounds/respawn behavior and permits optional per-scenery
`agilityShortcut` metadata. The generic packaging decoder lagged that contract.
No package header, metadata, content hash or map bytes were changed to bypass it.

The read-only tool now accepts v5 only with its actual supported structure:

- Exact optional shortcut keys and schema 1; the five matching jump, crawl,
  climb, swing and balance template/action pairs.
- Required/max levels in 1–99 with maximum at least required; starting/max
  chances in 0–100 with no decrease; equal levels require equal chances.
- Exact global destination records, x/y in 1–32,766 and signed 32-bit level.
- Existing placement keys/counts, IDs, slot uniqueness, NPC bounds/respawn,
  amounts, timers, directions, manifest identity and payload hashes remain
  checked. Older formats reject the extra shortcut field; future encodings,
  malformed values and unknown keys remain rejected.

The change ports structural checks from `AgilityShortcut.read` and the native
placement reader. It does not execute shortcuts or replace runtime destination
terrain/collision/eligibility checks. The package manifest schema's supported
encoding enum now includes already supported v4 and v5; no generic allow-all
extension or content-specific exception was added.

Normal packaged clients use `WorldBuilderInstalledClientProfile` and
`WorldBuilderTerrainBootstrap` to select exact manifest identity and
server-authored native geometry/scene updates. They do not decode placement
behavior payloads. The real built client's installed/native bootstrap passes
with a v5 fixture. The separate adaptive editor binding still accepts its own
v3/v4 contract unchanged. No client or game-runtime source change was needed.

## Verification and limits

Evidence remains in ignored `output/next-update-candidate/`, including exact
copied inventory, selection/config receipts, before/after validation logs,
commands, runtime artifact hashes and preservation checks. Heavy Java work ran
serially under `/home/justin/.local/share/core-heavy-build.lock`, Java 8 with
512 MiB SerialGC.

- `scripts/installed-map-selection.py`: PASS exact exported/source server/client
  inventory and profile pairing; both role copies retain their supplied hashes.
- `test-installed-map-release-selection.py`: PASS synthetic invalid-selection
  regressions.
- Maintained `layered_world_selected_pair` → full CLI `package-check`: PASS on
  the actual source-selected repaired package, including all 1,787 sectors,
  nine placement sets and levels. Validator output is under
  `tools/layered-maps/workspace/next-update-candidate-20261101/`.
- `test-layered-v5-release-validation.py`: PASS seven tests comprising 13 valid
  and 39 invalid package cases independently decoded by both tool and real
  native server reader, plus actual installed-client bootstrap. Cases cover
  v1–v4 retention, all five templates, empty/no-shortcut v5, chance/coordinate
  boundaries, malformed metadata, mismatched/future versions and retained
  placement guards. Every case verifies validators leave package bytes intact.
- Existing `NextUpdateMapAcceptanceTest`: PASS **510 checks, zero failures** on
  the new source package, using the retained acceptance classes and isolated
  `shore-edge-review/runtime-fixture`. This exercises maintained registration,
  collision/access, guards, scenery/door and well/ladder routes without accounts
  or listeners. It does not prove rendering, combat, player/rank acceptance,
  saving or database persistence.
- `git diff --check` and Python compilation pass. Game Java/plugins and
  definitions remain unchanged from `0eea4c88a`; only tool validation Java changes.

The manager will run combined final validation and create actual private
artifacts after integration. The migration rehearsal was separately reviewed
and integrated at `02243c3c398cff507504969b3b77317c287a9e4b`.
No database fixtures or accounts from that rehearsal are part of this map commit.

## Release documentation and remaining owner choices

`roadmap.md`, `next-major-release-goals.md` and
`next-update-migration-package.md` now distinguish prepared source selection
from remaining private acceptance. The goals also replace obsolete early
Silver-nugget/cost text with the implemented moulded blanks, Moonsilver and
current-plus-previous sigil grade costs, including Greater Unify's 3 Stone +
3 Silver Grace. This is documentation of existing approved behavior.

Player-facing draft notes are `release/player/NEXT-UPDATE-NOTES-DRAFT.txt`.
They cover the implemented tower/cave, gem drops/equipment, coatings, Cleric,
production, combat luck/hit numbers and reliability changes without claiming
unimplemented hostile spells or boss content. `ASSET-SOURCES.txt` points to
project additions/adaptations and their provenance; it grants no new rights to
underlying classic artwork and assumes no new Cleric icons or licence.

The owner's Cleric icons and final paired private gameplay/visual acceptance
remain pending. Provisional private `v0.3.0-alpha.1` is not a final public tag or
beta decision. Mystic/Fierce dragons, Corporeal Beast, Ante, Cleric Tier 4,
Zamorak hostile activation and the larger half-tile redesign remain deferred.
The already accepted concise Cleric UI is unchanged.


## Follow-up: selected hosting and private launch identity

The final manager pass found two remaining historical-pin assumptions in tests.
`test-hosted-alpha-config.py` now obtains the current manifest from the strict
selected pair rather than requiring the old fallback literal.
`test-layered-production-promotion.py` separately verifies the current
source-selected pair/promotion and the unchanged historical fallback's six
identity/count constants, exact inventory and maintained package validation.
It requires the generated `SPOILED_MILK_LAYERED_PACKAGE` environment input and a
fresh matching source-review receipt; no dirty-source approval bypass is added.

The same audit reproduced a real private launch issue: after generating the new
selected package, `layered_world_enable_private_production_profile` exported the
old fallback manifest. That route would pair the new package path with the wrong
hash. The helper now derives the manifest hash from the exact package that the
caller just validated/generated, rejecting a missing/unreadable hash before
exporting profile flags. The private launch banner reports that exported identity.
The strict pinned fallback validator and hosted installed-source guards are
unchanged. No server is launched by these tests.

Additional negative coverage rejects malformed/missing/symlink selectors without
entering an otherwise successful fallback, parser failures before private-profile
enabling, missing manifests, missing/malformed/unreviewed promotion reports,
changed source commits and dirty tracked/untracked source. Source-selected and
historical fallback private-profile hashes are both exercised. The complete
promotion fixture is run from a clean checkpoint using a freshly generated exact
selected-package receipt, preserving the real clean-source approval requirement.
Evidence and the final command results accompany the READY handoff under
`output/next-update-candidate/hosting-validation/`.

The nearest hosted safety/selection consumers retain their existing security
checks. Historical native-package generation baselines and retired editor-draft
fixture counts remain deliberate baseline tests rather than active map pins.
This follow-up does not change the map, production Java/definitions, icons or
public deployment. The manager's final clean-source release seal remains separate
from this worker's tests while the owner completes Cleric artwork.
