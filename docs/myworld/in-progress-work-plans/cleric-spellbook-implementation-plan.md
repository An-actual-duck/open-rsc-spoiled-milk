# Cleric Spellbook Implementation Plan

## Status

- Branch: `feat/cleric-holy-power-equipment`
- Governing design: [`cleric-spellbook-concept.md`](cleric-spellbook-concept.md)
- Completed milestones: **C01 — definition catalog foundation; C02 — sigil item and asset identities**
- Current milestone: **C03 — Holy Power equipment foundation**
- Next planned milestone after C03 review: **C04 — Blessing skill platform**
- Runtime exposure: **Holy Power equipment statistic only; Cleric production and spell gameplay remain disabled**
- Public-server work: **forbidden**

This plan orders implementation of the confirmed Cleric concept without
silently deciding its open design questions. The concept remains authoritative
for player-facing behavior and balance. This document owns implementation
sequencing, dependencies, verification, and stop conditions.

## Global Boundaries

- Preserve stable `Prayer`/skill-ID compatibility while presenting `Worship`
  to players.
- Do not add Cleric spells to the legacy `Spells` enum by ordinal. Cleric
  identities require their own explicit stable keys and numeric codes so future
  Magic additions cannot renumber them.
- Do not expose a spell, production action, skill, packet, or interface until
  every decision required by that surface is settled.
- Cleric casting never awards Worship XP. Worship remains offering-trained.
- Sigil carving belongs to Crafting; altar conversion and blessed-equipment
  production belong to Blessing.
- Casting never drains Devotion directly. Blessed sigils embody the Devotion
  paid during production.
- Do not introduce Holy Defense or move god-spell damage away from the existing
  Magic Power/Magic Defense authority.
- Keep authentic-client behavior safe. Custom presentation must be optional;
  server gameplay state must never depend on a custom HUD packet.
- Do not alter, restart, or deploy the public server from an implementation
  branch.

## Decisions That Block Runtime Exposure

The following remain unresolved in the concept and may not be guessed:

1. Whether the Cleric book is immediately available at the Worship requirement
   or requires an introductory unlock/quest.
2. Silver sigil input form and production quantity.
3. Crafting and Blessing levels, XP, batching, and failure behavior.
4. Exact half-offering accounting for odd sigil quantities without corrupting
   existing integer Devotion saves.
5. Blessing skill curve and all persistence, protocol, interface, highscores,
   cape, potion, and XP-modifier consequences.
6. Sigil consumption when an area cast has partial or no useful recipients.
7. PvP rules and abuse safeguards.
8. Status-HUD priority and overflow behavior at the existing 16-entry bound.
9. Unify movement-queue/client-presentation integration.
10. Respite passive-regeneration clock synchronization.
11. Offensive god-spell placement and unlock requirements.
12. Additional Devotion sources required to balance repeat sigil production.

Code may model already confirmed data adjacent to these questions, but it must
not register or invoke behavior that depends on an unresolved answer.

## Ordered Implementation Sequence

### C01 — Definition Catalog Foundation (current branch)

Create a server-side, content-neutral metadata source for the confirmed launch
roster:

- explicit stable code and key for all twelve Cleric spells;
- display name, alignment, Worship requirement, spell/resource tier, standard
  radius, caster-exclusion flag, and primary sigil vector;
- confirmed Holy Power threshold ladder and pure rank resolution;
- immutable collections, defensive copies, unique-key/code validation, and
  bounded launch-tier cost arithmetic;
- compiled fixture covering the complete roster, lookups, cost totals, radius
  rules, threshold boundaries, and rejection cases.

This milestone must not:

- register startup data;
- add packet handlers or modify `Spells`;
- expose a Cleric tab or casting action;
- add effects, items, production, Blessing, or persistence;
- decide any open casting/resource rule.

Acceptance gate:

- catalog tests and full server build pass;
- changed-code static analysis passes;
- a repository guard proves no gameplay handler or startup path references the
  new catalog;
- the exact pushed commit is independently useful to later spell work.

### C02 — Sigil Item and Asset Identities

Allocate stable item IDs and definitions for the confirmed four alignments,
two materials, and unblessed/blessed states. Preserve the eight supplied source
sprites from `/home/justin/Core-Framework/output/sigils` under a maintained
`dev/myworld/assets/sprites/items/inventory-ground/resources/sigils/` source
folder. Generate silver variants from the same symbols with a silver treatment
and smaller inventory footprint, without redrawing their religious marks.

This slice may make inert items visible to development clients, but it must not
add carving, altar conversion, spawning, shops, drops, spell consumption, or
other acquisition. Server/client item definitions, client asset lookup,
packaging, item-count bounds, and fallback behavior must agree. Confirm exact
names and derived silver dimensions during asset review; do not infer silver
production inputs or quantities.

Verification:

- item-ID integrity and client/server definition parity;
- PNG alpha/dimension checks and packaged-resource presence;
- classic fallback behavior when an external PNG is unavailable;
- client and server builds;
- private inventory/ground-icon inspection if inert development spawning is
  authorized.

Compatibility note: the legacy monolithic `ItemId` enum is already at the JVM
64-KiB generated-method limit and cannot safely accept the sixteen new enum
entries. `ClericSigilItemId` therefore owns their explicit stable numeric
identities, material, alignment, and blessing state. `ItemId.maxCustom` remains
the authoritative exclusive item-count boundary. Later Cleric code must use
the focused identity catalog rather than relying on enum ordinals or assuming
that every custom definition can be added to the legacy enum.

### C03 — Holy Power Equipment Foundation

Add Holy Power as an equipment-derived server stat and a client-displayed
equipment value. Map blessed staff tiers to the established full staff-power
ladder (`8/12/16/24/28/32/40/44/48/56`) and god staves to the tier-eleven
value `64`.
Ordinary Magic staves and blessed armor contribute zero. Any aligned blessed
or god staff empowers ordinary Cleric support regardless of spell alignment.

Synchronize blessed-staff Magic Power to exactly half its comparable ordinary
staff (`4/6/8/12/14/16/20/22/24/28`) and all god staves to `28`. Keep existing
offensive god spells on Magic Power and Magic Defense.

Verification:

- all three blessed lines and three god staves have parity;
- no-staff and ordinary-staff Holy Power remain zero;
- Magic Power comparisons and client display match the authoritative server;
- equipment mode compatibility, equip/unequip, relog, and definition tests;
- private equipment-panel inspection.

### C04 — Blessing Skill Platform

Only after the remaining skill decisions are approved, add Blessing as a real
skill across authoritative skill configuration, save/database round trips,
packets, client layouts, guides, commands, XP modifiers, highscores, and any
combat-level or total-level consumers. Preserve every existing skill index and
compatibility alias; append or otherwise migrate only through an explicitly
validated compatibility contract.

Stop if the level curve, client placement, persistence migration, or protocol
representation is not settled. Do not use Enchanting/RuneCraft aliases as an
implementation shortcut.

### C05 — Sigil Carving and Altar Blessing

Implement chisel-driven Crafting selection and full-inventory altar conversion
only after levels, XP, batching, silver inputs, and exact fractional Devotion
accounting are decided. Use centralized altar god identity and the existing
transaction pattern. Aligned sigils require their matching altar; neutral
sigils accept any god altar and charge that altar's god while producing one
neutral item identity. Eligibility is strictly above `-1000` Devotion.

Verification must cover 1, 2, 29, and 30-item batches, exact cumulative cost,
insufficient Devotion, inventory replacement failure, neutral altars, all
three aligned altars, rollback, relog, and no Worship XP.

### C06 — Cleric Spellbook Transport and Presentation

Add the independently identified Cleric catalog to server/client transport and
a client spellbook view without changing Magic ordinals. Keep it disabled
behind a non-live feature gate until the introductory-unlock decision and
resource-failure rules are approved. Show Worship gates, primary sigil vectors,
alignment, and effect descriptions from validated metadata rather than a
second hand-maintained name table.

Verification must cover stable identity parity, all twelve levels/costs,
classic-client safety, no Magic spell drift, and private layout inspection.

### C07 — Shared Support Targeting and Cast Transaction

Implement caster-centered square/Chebyshev party resolution, self exclusion,
world-space and signed-layer equality, per-recipient spell line-of-effect, and
one server-authoritative cast transaction. Standard radius is `tier + 1`;
Unify is radius four. Do not finalize sigil spend on partial/empty casts until
that open design question is approved. PvP eligibility also blocks this phase.

### C08 — Shared Cleric Effect State and HUD Extension

Implement transient effect identity, snapshotted rank, magnitude, expiry, and
optional charges/pulses. Enforce replacement/exclusivity centrally and clear
effects on death, logout, or party separation. Extend the existing potion HUD
packet compatibly with an optional count only after overflow priority is
settled. Authentic clients receive no custom dependency.

### C09 — Low-Risk Support Effects

Implement in narrow branches, beginning with mechanics that do not alter the
shared combat damage pipeline:

1. Purify;
2. Restore;
3. Mend and Greater Mend;
4. Respite after its regeneration-clock rule is resolved.

Each branch owns its pure calculations, lifecycle tests, area behavior,
resource transaction, client text/HUD behavior where applicable, builds, and
private verification.

### C10 — Direct-Combat Support Effects

Introduce one shared direct-damage eligibility model, then implement Fervor,
Zeal, Ward/Aegis, Thorns, and Rally in focused branches. Preserve existing
blood-item, god-spell, recoil, critical-hit, prayer, poison, summon, and kill
attribution behavior. Test melee, ranged, and Magic paths together so no effect
is accidentally implemented only in one handler.

### C11 — Unify Movement

Implement last among the launch spells because it crosses movement queues,
pathfinding, collision, layered maps, combat retention, and client movement
presentation. Move only up to two validated steps; never teleport. Stop until
the remaining queue/presentation decision is approved.

### C12 — Devotion Economy and Release Gate

Add approved Devotion sources, measure acquisition against expected sigil
consumption, and validate that ordinary offerings remain relevant. Complete
private party-combat and production testing, package all client assets, run the
full server/client/release suites, and retain feature gating until all twelve
spells and compatibility checks pass. Live activation is a separate manager
operation subject to the public-server shutdown gate.

## Supplied Sigil Asset Inventory

The owner supplied these source files on 2026-08-03:

- `unblessed-sara-sigil.png`
- `blessed-sara-sigil.png`
- `unblessed-guth-sigil.png`
- `blessed-guth-sigil.png`
- `unblessed-zam-sigil.png`
- `blessed-zam-sigil.png`
- `unblessed-neutral-sigil.png`
- `blessed-neutral-sigil.png`

They visibly distinguish all four primary alignments and the unblessed/blessed
state. C02 will preserve these as the stone-family source art. Silver versions
must retain the same symbols, use a silver palette treatment, and render
slightly smaller as requested.

## C01 Handoff Contract

The first handoff is complete only when:

- the implementation plan is checkpointed;
- the twelve-definition catalog compiles in isolation and in the full server;
- explicit keys/codes do not depend on `Spells.ordinal()`;
- all confirmed launch metadata in C01 is covered by executable assertions;
- cost and rank helpers reject invalid input and return immutable data;
- no production code outside the new foundation package references the catalog;
- no unresolved behavior has been implemented;
- the branch is clean, pushed, and marked READY with the exact commit.

## C01 Completion Record

C01 was completed on 2026-08-03 as an intentionally unreachable server-side
foundation. It adds explicit Cleric spell identities, immutable definition and
cost records, the twelve-entry launch catalog, and pure Holy Power effect-rank
resolution. Nothing registers the catalog with server startup, packet
handling, the legacy Magic spell list, an item, or a player-facing action.

The compiled fixture validates every confirmed launch name, alignment,
Worship gate, tier, radius, caster-exclusion rule, primary-sigil vector, stable
identity, and Holy Power threshold. It also covers collection immutability,
defensive copying, rank boundaries, invalid identities, invalid costs, and the
deliberate rejection of unsettled tier-three costs. A source guard fails if
production code outside the foundation package begins referencing it early.

Verification completed from the milestone branch:

- `python3 tests/myworld/test-cleric-spellbook-foundation.py`
- `./scripts/build-server.sh`
- `./scripts/lint.sh compiler --base 40d2407cb047742e52070e41fc5ef9d865bf77f5 --offline`
- `./scripts/lint.sh analyze --base 40d2407cb047742e52070e41fc5ef9d865bf77f5 --offline`
- `git diff --check`

No client or visual verification is required for C01 because it deliberately
has no presentation or runtime path. The supplied sigil PNGs remain untouched
at their source location for C02, when their stable item identities, maintained
asset locations, generated silver variants, and classic fallbacks can be added
and verified together. All unresolved runtime-exposure blockers above remain
open and continue to stop later dependent phases.

## C02 Completion Record

C02 establishes sixteen inert, stackable item definitions at stable IDs
`3293-3308`: four alignments, stone and silver materials, and unblessed and
blessed states. It does not provide any acquisition or use path. The server
definitions and direct client registry agree on names, descriptions, flags,
IDs, and the exclusive item count of `3309`.

The owner's eight supplied sigil images and plain `stone.png` reference are
preserved byte-for-byte in the maintained sigil asset folder. Eight derived
silver variants recolor only pixels belonging to the supplied stone substrate
palette; every religious and neutral symbol pixel is unchanged. Maintained
images remain `28x25` RGBA files. Client metadata renders stone at `28x25` and
silver at `24x21`, using nearest-neighbor scaling for the requested smaller
silver footprint.

Enhanced clients search the focused maintained folder and package all PNGs in
the client JAR. Missing external art remains safe: stone definitions fall back
to authentic stone sprite `443`, while silver definitions fall back to
authentic silver sprite `134`. Classic presentation therefore never depends on
the new external artwork.

The focused compiled fixtures validate stable identity lookups and rejection,
all sixteen server/client records, source-art hashes, exact substrate-only
silver transformation, alpha and dimensions, development and packaged loading,
render bounds, packaged-resource presence, and the missing-PNG fallback path.
Source guards keep the identities unreachable from production gameplay until a
later approved phase. Verification for this milestone includes:

- `python3 tests/myworld/test-cleric-spellbook-foundation.py`
- `python3 tests/myworld/test-cleric-sigil-item-assets.py`
- `python3 tests/myworld/test-client-definition-registry-extraction.py`
- `python3 tests/myworld/audit-item-id-integrity.py`
- `python3 tests/myworld/audit_client_item_coverage.py`
- `./scripts/build-client.sh`
- `./scripts/build-server.sh`
- changed-code compiler and static analysis
- `git diff --check`

C02 deliberately adds no carving, altar conversion, Devotion accounting,
Blessing behavior, spawn/drop/shop source, spell consumption, dialogue, packet,
or player-visible spell functionality. All global runtime blockers remain open.
